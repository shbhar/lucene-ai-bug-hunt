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
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Behaviour of the "mixed marker" fix (run with the fix only): what queries see after a mix, and
 * when the field becomes queryable again. Each test passes when the stated facts hold.
 */
public class TestMixedMarkerFixProbe extends LuceneTestCase {

  private static final String FIELD = "suggest_field";

  private static IndexWriterConfig iwc(MergePolicy mergePolicy) {
    Codec codec =
        new FilterCodec(TestUtil.getDefaultCodec().getName(), TestUtil.getDefaultCodec()) {
          final PostingsFormat completion = new Completion104PostingsFormat();

          @Override
          public PostingsFormat postingsFormat() {
            return new PerFieldPostingsFormat() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                return FIELD.equals(field)
                    ? completion
                    : ((PerFieldPostingsFormat) delegate.postingsFormat())
                        .getPostingsFormatForField(field);
              }
            };
          }
        };
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(codec);
    iwc.setMergePolicy(mergePolicy);
    iwc.setMergeScheduler(new SerialMergeScheduler());
    return iwc;
  }

  private static Document doc(String id, Field suggest) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.NO));
    doc.add(suggest);
    return doc;
  }

  /** Returns the number of suggestions, or -1 if the query is rejected as "multiple types". */
  private static int suggestions(Directory dir) throws Exception {
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
      try {
        return searcher.suggest(
                new PrefixCompletionQuery(new MockAnalyzer(random()), new Term(FIELD, "ap")),
                10,
                false)
            .scoreLookupDocs()
            .length;
      } catch (IllegalStateException e) {
        assertTrue(e.getMessage(), e.getMessage().contains("has values of multiple types"));
        return -1;
      }
    }
  }

  /** Two committed segments: the merge succeeds; queries are rejected until one type is gone. */
  public void testMergedSegmentsRejectQueriesUntilOneTypeIsDeleted() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, iwc(new LogDocMergePolicy()));
      w.addDocument(doc("1", new SuggestField(FIELD, "apple", 1)));
      w.commit();
      w.addDocument(doc("2", new ContextSuggestField(FIELD, "apricot", 1, "ctx")));
      w.commit();
      assertEquals("two segments of different types", -1, suggestions(dir));
      w.forceMerge(1);
      w.commit();
      assertTrue(w.isOpen());
      assertEquals("one merged, mixed segment", -1, suggestions(dir));
      w.deleteDocuments(new Term("id", "2"));
      w.forceMerge(1);
      w.commit();
      assertEquals("after deleting the ContextSuggestField document and merging", 1, suggestions(dir));
      w.close();
    }
  }

  /**
   * The sibling shape (a buffered SuggestField document updated to a ContextSuggestField one): the
   * flush no longer closes the writer, but the deleted SuggestField posting still counts, so the
   * segment is marked mixed until a merge drops it.
   */
  public void testUpdatedDocumentMarksSegmentMixedUntilMerged() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, iwc(NoMergePolicy.INSTANCE));
      w.addDocument(doc("1", new SuggestField(FIELD, "apple", 1)));
      w.updateDocument(
          new Term("id", "1"), doc("1", new ContextSuggestField(FIELD, "apple", 1, "ctx")));
      w.commit();
      assertTrue(w.isOpen());
      assertEquals("flushed segment with a deleted SuggestField posting", -1, suggestions(dir));
      w.close();
      try (IndexWriter w2 = new IndexWriter(dir, iwc(new LogDocMergePolicy()))) {
        w2.forceMerge(1);
      }
      assertEquals("after a merge drops the deleted posting", 1, suggestions(dir));
    }
  }
}
