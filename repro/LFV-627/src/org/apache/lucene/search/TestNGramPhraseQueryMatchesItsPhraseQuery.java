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
package org.apache.lucene.search;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.ngram.NGramTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * NGramPhraseQuery is documented as a PhraseQuery "optimized for n-gram phrase query": it rewrites
 * "AB/0 BC/1 CD/2" to "AB/0 CD/2". The rewrite is only equivalent when consecutive gram positions
 * come from consecutive characters. On ordinary n-gram fields where that does not hold, the
 * rewritten query matches documents that the PhraseQuery it wraps does not match.
 */
public class TestNGramPhraseQueryMatchesItsPhraseQuery extends LuceneTestCase {

  /** Indexes one document with the given values and checks both queries match the same docs. */
  private void assertSameMatches(Analyzer analyzer, String phrase, String... values)
      throws Exception {
    try (Directory dir = newDirectory()) {
      try (RandomIndexWriter w = new RandomIndexWriter(random(), dir, analyzer)) {
        Document doc = new Document();
        for (String v : values) {
          doc.add(new TextField("f", v, Field.Store.NO));
        }
        w.addDocument(doc);
        try (IndexReader reader = w.getReader()) {
          IndexSearcher searcher = newSearcher(reader);
          PhraseQuery pq = (PhraseQuery) new QueryBuilder(analyzer).createPhraseQuery("f", phrase);
          NGramPhraseQuery ngpq = new NGramPhraseQuery(2, pq);
          assertEquals(
              "PhraseQuery " + pq + " vs NGramPhraseQuery rewritten to " + searcher.rewrite(ngpq),
              searcher.count(pq),
              searcher.count(ngpq));
        }
      }
    }
  }

  /** Plain NGramTokenizer(2, 2), two values, Analyzer's default position increment gap (0). */
  public void testMultiValuedBigramField() throws Exception {
    Analyzer bigrams =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            return new TokenStreamComponents(new NGramTokenizer(2, 2));
          }
        };
    // values "xab" and "zcd" index xa/0 ab/1 zc/2 cd/3; the phrase "abcd" needs ab bc cd
    assertSameMatches(bigrams, "abcd", "xab", "zcd");
  }

  /** CJKAnalyzer (bigrams), single value: a sentence break restarts the bigrams without a gap. */
  public void testCJKBigramField() throws Exception {
    // phrase: U+6771 U+4EAC U+5927 U+5B66; document: U+6771 U+4EAC U+3002 U+65B0 U+5927 U+5B66
    // the document has the first and the last bigram of the phrase two positions apart, but not
    // the middle bigram and not the phrase
    assertSameMatches(new CJKAnalyzer(), "東京大学", "東京。新大学");
  }

  /** NGramTokenizer pre-tokenizing on whitespace through isTokenChar, as its javadoc suggests. */
  public void testPreTokenizingBigramField() throws Exception {
    Analyzer bigrams =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t =
                new NGramTokenizer(2, 2) {
                  @Override
                  protected boolean isTokenChar(int chr) {
                    return Character.isWhitespace(chr) == false;
                  }
                };
            return new TokenStreamComponents(t);
          }
        };
    assertSameMatches(bigrams, "abcd", "xab zcd");
  }
}
