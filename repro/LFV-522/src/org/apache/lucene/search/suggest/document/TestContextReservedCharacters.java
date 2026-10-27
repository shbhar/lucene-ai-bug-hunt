/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.search.suggest.document;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * ContextSuggestField documents "@throws IllegalArgumentException ... if value or contexts contains any reserved
 * characters", and SuggestField rejects U+001E and U+001F in the value. Contexts are only checked for U+001D, so a
 * context containing U+001E or U+001F is accepted and silently rewritten by the completion analysis chain.
 */
public class TestContextReservedCharacters extends LuceneTestCase {

  /**
   * The documented contract: reserved characters in a context are rejected like in the value. As in the upstream
   * TestContextSuggestField#testReservedChars (U+001D), the check may fire at construction or at addDocument.
   */
  public void testReservedCharactersInContextAreRejected() throws Exception {
    // control: U+001E and U+001F are rejected in the value
    expectThrows(
        IllegalArgumentException.class, () -> new ContextSuggestField("suggest", "a\u001Eb", 1, "c"));
    expectThrows(
        IllegalArgumentException.class, () -> new ContextSuggestField("suggest", "a\u001Fb", 1, "c"));
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new WhitespaceAnalyzer());
      iwc.setCodec(completionCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        // control: U+001D in a context is rejected (upstream behaviour)
        expectThrows(IllegalArgumentException.class, () -> addContextDoc(w, "a\u001Db"));
        // U+001E (HOLE) and U+001F (SEP_LABEL) in a context must be rejected as well
        expectThrows(IllegalArgumentException.class, () -> addContextDoc(w, "a\u001Eb"));
        expectThrows(IllegalArgumentException.class, () -> addContextDoc(w, "a\u001Fb"));
      }
    }
  }

  private static void addContextDoc(IndexWriter w, String context) throws Exception {
    Document d = new Document();
    d.add(new ContextSuggestField("suggest", "value", 1, context));
    w.addDocument(d);
  }

  /** Consequence: a context containing U+001E is indexed as the context without it ("ab"). */
  public void testHoleCharacterContextCollapsesIntoAnotherContext() throws Exception {
    List<String> hits = suggestWithExactContext(new String[] {"ab", "a\u001Eb"}, "ab");
    if (hits == null) {
      return; // rejected at indexing time, as documented
    }
    // only the document indexed under context "ab" may match the exact context "ab"
    assertEquals(List.of("doc0 ctx=ab"), hits);
  }

  /** Consequence: a context containing U+001F cannot be matched exactly and is reported changed. */
  public void testSeparatorCharacterContextIsUnreachableAndReportedChanged() throws Exception {
    List<String> exact = suggestWithExactContext(new String[] {"a\u001Fb"}, "a\u001Fb");
    List<String> all = suggestWithExactContext(new String[] {"a\u001Fb"}, null);
    if (exact == null && all == null) {
      return; // rejected at indexing time, as documented
    }
    assertEquals("all contexts", List.of("doc0 ctx=a<1F>b"), all);
    assertEquals("exact context", List.of("doc0 ctx=a<1F>b"), exact);
  }

  /**
   * Indexes one document per context (suggestion "value"), queries prefix "val" with an exact context or all.
   * Returns null if indexing rejected a context with IllegalArgumentException.
   */
  private List<String> suggestWithExactContext(String[] contexts, String exactContext) throws Exception {
    Analyzer analyzer = new WhitespaceAnalyzer();
    List<String> out = new ArrayList<>();
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
      iwc.setCodec(completionCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < contexts.length; i++) {
          Document d = new Document();
          d.add(new StringField("id", "doc" + i, Field.Store.YES));
          d.add(new ContextSuggestField("suggest", "value", 10 - i, contexts[i]));
          try {
            w.addDocument(d);
          } catch (IllegalArgumentException e) {
            return null;
          }
        }
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
        ContextQuery query =
            new ContextQuery(new PrefixCompletionQuery(analyzer, new Term("suggest", "val")));
        if (exactContext == null) {
          query.addAllContexts();
        } else {
          query.addContext(exactContext);
        }
        for (TopSuggestDocs.SuggestScoreDoc hit : searcher.suggest(query, 10, false).scoreLookupDocs()) {
          String ctx = hit.context == null ? "null" : hit.context.toString().replace("\u001F", "<1F>");
          out.add(reader.storedFields().document(hit.doc).get("id") + " ctx=" + ctx);
        }
      }
    }
    return out;
  }

  private static Codec completionCodec() {
    String delegate = TestUtil.getDefaultPostingsFormat().getName();
    final PostingsFormat completion =
        PostingsFormat.forName("Completion" + delegate.substring("Lucene".length()));
    final PostingsFormat perField =
        new PerFieldPostingsFormat() {
          @Override
          public PostingsFormat getPostingsFormatForField(String field) {
            return field.equals("suggest") ? completion : TestUtil.getDefaultPostingsFormat();
          }
        };
    Codec base = TestUtil.getDefaultCodec();
    return new FilterCodec(base.getName(), base) {
      @Override
      public PostingsFormat postingsFormat() {
        return perField;
      }
    };
  }
}
