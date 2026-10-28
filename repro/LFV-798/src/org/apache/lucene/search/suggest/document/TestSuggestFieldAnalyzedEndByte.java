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
 * SuggestField rejects U+0000 (NRTSuggesterBuilder.END_BYTE, the byte that ends every analyzed form in the
 * suggester's FST) only in the raw value. The analyzed form is not checked, so a stock char filter that decodes
 * the character reference "&#0;" (HTMLStripCharFilter) puts the reserved byte into the FST input:
 *
 * <ul>
 *   <li>the FST input of "a" becomes a proper prefix of the input of "a&#0;", and the lookup loses the plain
 *       suggestion "a" (it is no longer returned for the query "a");
 *   <li>with two entries for "a", the inputs are added out of order: with assertions enabled the flush fails with
 *       an AssertionError that closes the IndexWriter and loses every buffered document; without assertions the
 *       FST is built from the out-of-order inputs and a suggestion becomes unreachable.
 * </ul>
 *
 * <p>Oracle: the same analyzer with "&#1;" (decoded to U+0001, not reserved) indexes and finds every suggestion. A
 * fixed analyzer chain may either reject the reserved analyzed form at addDocument (IllegalArgumentException, like
 * the raw-value check) or index it correctly; it must not hide other suggestions or close the writer.
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

  /** Indexes one document per value and checks that every accepted document is found by its own value. */
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
        } catch (IllegalArgumentException e) {
          // acceptable: the reserved character is rejected up front, as for the raw value
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
          TopSuggestDocs hits =
              searcher.suggest(
                  new PrefixCompletionQuery(analyzer, new Term("suggest", values[i])), 100, false);
          Set<Integer> found = new HashSet<>();
          for (TopSuggestDocs.SuggestScoreDoc hit : hits.scoreLookupDocs()) {
            found.add(reader.storedFields().document(hit.doc).getField("id").numericValue().intValue());
          }
          assertTrue(
              "suggestion '" + values[i] + "' (doc " + i + ") not returned for its own value; got docs " + found,
              found.contains(i));
        }
      }
      writer.close();
    }
  }

  /** Control: U+0001 is not reserved; every suggestion is found. Passes on main. */
  public void testControlU0001() throws Exception {
    indexAndCheck("a", "a&#1;");
    indexAndCheck("a&#1;", "a", "a", "b");
  }

  /** The plain suggestion "a" disappears once another document's value analyzes to "a" + U+0000. */
  public void testPlainSuggestionHiddenByAnalyzedEndByte() throws Exception {
    indexAndCheck("a", "a&#0;");
  }

  /**
   * Two entries for "a" plus "a" + U+0000 + U+0000: the FST inputs are added out of order at flush. With
   * assertions the flush fails and closes the writer; without them the FST is built anyway and the suggestion
   * "a&#0;&#0;" can no longer be found.
   */
  public void testFlushWithAnalyzedEndByte() throws Exception {
    indexAndCheck("a&#0;&#0;", "a", "a", "b");
  }
}
