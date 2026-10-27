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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
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
 * ShingleFilter with minShingleSize >= 3 and unigrams on (the default): when a filler position
 * (a hole left by a removed stop word) is followed by fewer than minShingleSize positions, the
 * filler unigram is skipped as all-filler, no shingle fits, and incrementToken() returns false, so
 * the remaining input tokens are never output as unigrams.
 */
public class TestShingleFilterTrailingHoleUnigrams extends BaseTokenStreamTestCase {

  private static final CharArraySet STOP = new CharArraySet(Arrays.asList("a", "of", "the"), false);

  private static Analyzer analyzer(int min, int max) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tok = new WhitespaceTokenizer();
        TokenStream ts = new StopFilter(tok, STOP);
        Map<String, String> args = new HashMap<>();
        args.put("minShingleSize", Integer.toString(min));
        args.put("maxShingleSize", Integer.toString(max));
        return new TokenStreamComponents(tok, new ShingleFilterFactory(args).create(ts));
      }
    };
  }

  /** setOutputUnigrams javadoc: the output contains the input tokens (unigrams) as well as shingles. */
  public void testLastWordAfterStopWordsIsOutput() throws Exception {
    // control: minShingleSize 2
    try (Analyzer a = analyzer(2, 3)) {
      assertAnalyzesTo(
          a,
          "war of the worlds",
          new String[] {"war", "war _", "war _ _", "_ _ worlds", "_ worlds", "worlds"});
    }
    // control: no hole
    try (Analyzer a = analyzer(3, 3)) {
      assertAnalyzesTo(a, "war worlds", new String[] {"war", "worlds"});
    }
    // minShingleSize 3: the unigram "worlds" must still be there
    try (Analyzer a = analyzer(3, 3)) {
      assertAnalyzesTo(
          a, "war of the worlds", new String[] {"war", "war _ _", "_ _ worlds", "worlds"});
    }
  }

  /** A single word after a stop word: the field produces no token at all. */
  public void testSingleWordAfterStopWord() throws Exception {
    try (Analyzer a = analyzer(3, 4)) {
      assertAnalyzesTo(a, "the cat", new String[] {"cat"});
    }
  }

  /** The same through the stock ShingleAnalyzerWrapper around EnglishAnalyzer (stop words, stemming). */
  public void testShingleAnalyzerWrapper() throws Exception {
    try (Analyzer english = new EnglishAnalyzer();
        Analyzer a = new ShingleAnalyzerWrapper(english, 3, 3)) {
      assertAnalyzesTo(
          a, "the lord of the rings", new String[] {"_ lord _", "lord", "lord _ _", "_ _ ring", "ring"});
    }
  }

  /** The word is not searchable. */
  public void testTermQueryMisses() throws Exception {
    try (Analyzer a = analyzer(3, 3);
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
