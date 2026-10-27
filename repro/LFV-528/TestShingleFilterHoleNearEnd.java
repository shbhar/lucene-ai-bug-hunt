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
package org.apache.lucene.analysis.shingle;

import java.io.IOException;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * With minShingleSize &gt;= 3, a hole (here a removed stop word) near the end of the input must not
 * end the stream before the remaining words are output as unigrams.
 */
public class TestShingleFilterHoleNearEnd extends BaseTokenStreamTestCase {

  private static final CharArraySet STOP = new CharArraySet(List.of("a", "of", "the"), false);

  private static Analyzer analyzer(
      int min, int max, boolean outputUnigrams, boolean outputUnigramsIfNoShingles) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new WhitespaceTokenizer();
        ShingleFilter shingles = new ShingleFilter(new StopFilter(tokenizer, STOP), min, max);
        shingles.setOutputUnigrams(outputUnigrams);
        shingles.setOutputUnigramsIfNoShingles(outputUnigramsIfNoShingles);
        return new TokenStreamComponents(tokenizer, shingles);
      }
    };
  }

  /** "tea" is the last word and follows a hole: its unigram must be output. */
  public void testLastWordAfterHole() throws IOException {
    try (Analyzer a = analyzer(3, 3, true, false)) {
      assertAnalyzesTo(
          a,
          "a cup of tea",
          new String[] {"_ cup _", "cup", "cup _ tea", "tea"},
          new int[] {1, 1, 0, 2});
    }
  }

  /** With minShingleSize 4, the last two words follow the hole. */
  public void testLastTwoWordsAfterHole() throws IOException {
    try (Analyzer a = analyzer(4, 4, true, false)) {
      assertAnalyzesTo(
          a,
          "one of two three",
          new String[] {"one", "one _ two three", "two", "three"},
          new int[] {1, 0, 2, 1});
    }
  }

  /** A stop word and one word: the word must be output, also through outputUnigramsIfNoShingles. */
  public void testSingleWordAfterHole() throws IOException {
    try (Analyzer a = analyzer(3, 3, true, false)) {
      assertAnalyzesTo(a, "the cat", new String[] {"cat"}, new int[] {2});
    }
    try (Analyzer a = analyzer(3, 3, false, true)) {
      assertAnalyzesTo(a, "the cat", new String[] {"cat"}, new int[] {2});
    }
  }

  /** Controls: minShingleSize 2 with the same hole, and minShingleSize 3 without a hole. */
  public void testControls() throws IOException {
    try (Analyzer a = analyzer(2, 2, true, false)) {
      assertAnalyzesTo(
          a,
          "a cup of tea",
          new String[] {"_ cup", "cup", "cup _", "_ tea", "tea"},
          new int[] {1, 1, 0, 1, 1});
    }
    try (Analyzer a = analyzer(3, 3, true, false)) {
      assertAnalyzesTo(a, "cup tea", new String[] {"cup", "tea"}, new int[] {1, 1});
    }
  }

  /** End to end: a term query for the last word must find the document. */
  public void testTermQueryFindsLastWord() throws IOException {
    try (Analyzer a = analyzer(3, 3, true, false);
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new TextField("title", "a cup of tea", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        assertEquals(1, s.count(new TermQuery(new Term("title", "cup"))));
        assertEquals(1, s.count(new TermQuery(new Term("title", "tea"))));
      }
    }
  }
}
