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

import java.io.Reader;
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.charfilter.HTMLStripCharFilter;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * SuggestField rejects U+0000 (NRTSuggesterBuilder.END_BYTE, the byte that ends every analyzed form
 * in the suggester's FST) only in the raw value. HTMLStripCharFilter decodes the character
 * reference {@code &#0;} to U+0000, so the reserved byte reaches the FST through the analyzed form.
 * The control uses {@code &#1;} (U+0001, not reserved) and passes.
 */
public class TestSuggestFieldAnalyzedEndByte extends LuceneTestCase {

  static Analyzer htmlStripKeyword() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new KeywordTokenizer());
      }

      @Override
      protected Reader initReader(String fieldName, Reader reader) {
        return new HTMLStripCharFilter(reader);
      }
    };
  }

  static IndexWriterConfig iwc(Analyzer analyzer) {
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    Codec codec =
        new FilterCodec(TestUtil.getDefaultCodec().getName(), TestUtil.getDefaultCodec()) {
          final PostingsFormat completion = new Completion104PostingsFormat();

          @Override
          public PostingsFormat postingsFormat() {
            return new PerFieldPostingsFormat() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                if (field.equals("suggest")) {
                  return completion;
                }
                return ((PerFieldPostingsFormat) delegate.postingsFormat())
                    .getPostingsFormatForField(field);
              }
            };
          }
        };
    iwc.setCodec(codec);
    return iwc;
  }

  /**
   * Indexes document i with the suggestion values[i] and weight i + 1, commits, and checks that a
   * prefix query for each accepted value returns that document. An IllegalArgumentException at
   * addDocument (a rejected value) is accepted, as for a reserved character in the raw value.
   */
  private static void indexAndCheck(String... values) throws Exception {
    Analyzer analyzer = htmlStripKeyword();
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter writer = new IndexWriter(dir, iwc(analyzer));
      Set<Integer> accepted = new HashSet<>();
      for (int i = 0; i < values.length; i++) {
        Document doc = new Document();
        doc.add(new StoredField("id", i));
        doc.add(new SuggestField("suggest", values[i], i + 1));
        try {
          writer.addDocument(doc);
          accepted.add(i);
        } catch (IllegalArgumentException _) {
          // a rejected value is fine
        }
      }
      try {
        writer.commit();
      } catch (Throwable t) {
        throw new AssertionError(
            "commit failed; writer open=" + writer.isOpen() + ", buffered documents lost", t);
      }
      try (DirectoryReader reader = DirectoryReader.open(writer)) {
        SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
        for (int i : accepted) {
          Term term = new Term("suggest", values[i]);
          TopSuggestDocs hits =
              searcher.suggest(new PrefixCompletionQuery(analyzer, term), 100, false);
          Set<Integer> found = new HashSet<>();
          for (TopSuggestDocs.SuggestScoreDoc hit : hits.scoreLookupDocs()) {
            found.add(
                reader.storedFields().document(hit.doc).getField("id").numericValue().intValue());
          }
          assertTrue(
              "suggestion '"
                  + values[i]
                  + "' (doc "
                  + i
                  + ") not returned for its own value; got docs "
                  + found,
              found.contains(i));
        }
      }
      writer.close();
    }
  }

  /** Control: U+0001 is not reserved; every suggestion is found. */
  public void testControlU0001() throws Exception {
    indexAndCheck("a", "a&#1;");
    indexAndCheck("a&#1;", "a", "a", "b");
  }

  /** {@code "a"} (weight 1) is not returned once {@code "a&#0;"} (weight 2) is in the segment. */
  public void testPlainSuggestionHiddenByAnalyzedEndByte() throws Exception {
    indexAndCheck("a", "a&#0;");
  }

  /**
   * Two entries for "a" plus one value that analyzes to "a" + U+0000 + U+0000: the FST inputs are
   * added out of order at flush. With assertions the commit fails and closes the writer; without
   * them {@code "a&#0;&#0;"} is not found for its own value.
   */
  public void testFlushWithAnalyzedEndByte() throws Exception {
    indexAndCheck("a&#0;&#0;", "a", "a", "b");
  }
}
