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
package org.apache.lucene.analysis.miscellaneous;

import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.GENERATE_WORD_PARTS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.IGNORE_KEYWORDS;

import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.util.QueryBuilder;

/**
 * WordDelimiterGraphFilter swallows a token made only of delimiters (e.g. "/") and leaves a
 * position hole in its place; the next token gets the accumulated position increment
 * (upstream TestWordDelimiterGraphFilter#testPositionIncrements: "LUCENE / SOLR" gives SOLR a
 * position increment of 2). With IGNORE_KEYWORDS, a keyword token is returned before that
 * accumulated increment is applied, so the hole moves to after the keyword token.
 */
public class TestWdgfKeywordPendingHole extends BaseTokenStreamTestCase {

  private static Analyzer analyzer(CharArraySet keywords) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new MockTokenizer(MockTokenizer.WHITESPACE, false);
        TokenStream ts = new SetKeywordMarkerFilter(tokenizer, keywords);
        return new TokenStreamComponents(
            tokenizer,
            new WordDelimiterGraphFilter(ts, GENERATE_WORD_PARTS | IGNORE_KEYWORDS, null));
      }
    };
  }

  /** Control: nothing is a keyword; "/" leaves a hole before "solr" (passes on main). */
  public void testControlNoKeyword() throws Exception {
    try (Analyzer a = analyzer(CharArraySet.EMPTY_SET)) {
      assertAnalyzesTo(
          a, "lucene / solr rocks", new String[] {"lucene", "solr", "rocks"}, new int[] {1, 2, 1});
    }
  }

  /**
   * Same text, but "solr" is a keyword. IGNORE_KEYWORDS only suppresses the processing of "solr";
   * the hole left by "/" must stay before "solr". On main: position increments are {1, 1, 2}.
   */
  public void testKeywordAfterSwallowedToken() throws Exception {
    try (Analyzer a = analyzer(new CharArraySet(List.of("solr"), false))) {
      assertAnalyzesTo(
          a, "lucene / solr rocks", new String[] {"lucene", "solr", "rocks"}, new int[] {1, 2, 1});
    }
  }

  /**
   * End-to-end consequence: with the same analyzer at index and query time, the phrase "solr rocks"
   * no longer matches a document that contains exactly "solr rocks", because the hole was moved
   * between the two words.
   */
  public void testPhraseOfAdjacentWordsMisses() throws Exception {
    try (Analyzer a = analyzer(new CharArraySet(List.of("solr"), false));
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new Field("body", "lucene / solr rocks", TextField.TYPE_NOT_STORED));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        Query phrase = new QueryBuilder(a).createPhraseQuery("body", "solr rocks");
        assertEquals("phrase " + phrase, 1, s.count(phrase));
      }
    }
  }
}
