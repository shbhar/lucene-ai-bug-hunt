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

import java.io.IOException;
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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.util.QueryBuilder;

/**
 * WordDelimiterGraphFilter drops a token made only of delimiters ("/") and leaves a hole in its
 * place. With IGNORE_KEYWORDS, that hole must stay in front of the next token when that token is a
 * keyword, exactly as it does for a plain word or a protected word.
 */
public class TestWordDelimiterGraphFilterKeywordHole extends BaseTokenStreamTestCase {

  private static final CharArraySet SOLR = new CharArraySet(List.of("solr"), false);

  private static Analyzer analyzer(CharArraySet keywords, CharArraySet protWords) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new MockTokenizer(MockTokenizer.WHITESPACE, false);
        TokenStream ts = new SetKeywordMarkerFilter(tokenizer, keywords);
        return new TokenStreamComponents(
            tokenizer,
            new WordDelimiterGraphFilter(ts, GENERATE_WORD_PARTS | IGNORE_KEYWORDS, protWords));
      }
    };
  }

  /** The hole left by "/" stays in front of "solr", whether "solr" is a keyword or not. */
  public void testKeywordAfterSwallowedTokenKeepsHole() throws Exception {
    String text = "lucene / solr rocks";
    String[] terms = {"lucene", "solr", "rocks"};
    int[] posIncs = {1, 2, 1};
    // controls (pass): "solr" is a plain word, then a protected word
    try (Analyzer a = analyzer(CharArraySet.EMPTY_SET, null)) {
      assertAnalyzesTo(a, text, terms, posIncs);
    }
    try (Analyzer a = analyzer(CharArraySet.EMPTY_SET, SOLR)) {
      assertAnalyzesTo(a, text, terms, posIncs);
    }
    // "solr" is a keyword: the positions must not change
    try (Analyzer a = analyzer(SOLR, null)) {
      assertAnalyzesTo(a, text, terms, posIncs);
    }
  }

  /** The exact phrase "solr rocks" must match "lucene / solr rocks". */
  public void testPhraseAfterKeywordMatches() throws Exception {
    assertEquals(1, countPhrase("solr rocks", "lucene / solr rocks", "lucene / solr"));
  }

  /** The exact phrase "lucene solr" must not match: "/" sits between the two words. */
  public void testPhraseAcrossHoleDoesNotMatch() throws Exception {
    assertEquals(0, countPhrase("lucene solr", "lucene / solr rocks", "lucene / solr"));
  }

  /** Indexes each text as one document and counts the documents that match the exact phrase. */
  private int countPhrase(String phrase, String... texts) throws IOException {
    try (Analyzer a = analyzer(SOLR, null);
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(a))) {
        for (String text : texts) {
          Document doc = new Document();
          doc.add(new TextField("body", text, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        return newSearcher(r).count(new QueryBuilder(a).createPhraseQuery("body", phrase));
      }
    }
  }
}
