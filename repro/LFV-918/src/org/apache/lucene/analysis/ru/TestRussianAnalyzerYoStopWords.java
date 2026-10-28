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
package org.apache.lucene.analysis.ru;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.QueryBuilder;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * The bundled Russian stop list (russian_stop.txt, from Snowball) says in its header "letter `ё'
 * is translated to `е'": its entries are written with е and assume the input has already been
 * folded. RussianAnalyzer runs its StopFilter before the Snowball RussianStemmer, which is the only
 * component of the chain that folds ё to е. So a default stop word written with ё (её, ещё, всё,
 * неё, чём) is not removed and is indexed, although the same analyzer removes it when it is written
 * with е, and although the analyzer without stop words maps both spellings to the same term.
 */
public class TestRussianAnalyzerYoStopWords extends BaseTokenStreamTestCase {

  /** Default stop list entry (spelled with е, as the list header says) -> the same word with ё. */
  private static final String[][] PAIRS = {
    {"ее", "её"}, // her
    {"еще", "ещё"}, // still, yet, more
    {"все", "всё"}, // all / everything
    {"нее", "неё"}, // her (after a preposition)
    {"чем", "чём"}, // than / (about) what
  };

  private static List<String> terms(Analyzer a, String text) throws Exception {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  /** Control: the stop list holds the е spelling and the analyzer removes it. */
  public void testStopWordSpelledWithIeIsRemoved() throws Exception {
    try (Analyzer a = new RussianAnalyzer()) {
      for (String[] p : PAIRS) {
        assertTrue(p[0], RussianAnalyzer.getDefaultStopSet().contains(p[0]));
        assertAnalyzesTo(a, p[0], new String[0]);
      }
    }
  }

  /**
   * Control: without stop words the chain itself treats both spellings as one word (the Snowball
   * stemmer replaces every ё with е before stemming).
   */
  public void testChainWithoutStopWordsMapsBothSpellingsToTheSameTerm() throws Exception {
    try (Analyzer a = new RussianAnalyzer(CharArraySet.EMPTY_SET)) {
      for (String[] p : PAIRS) {
        assertEquals(p[0] + " vs " + p[1], terms(a, p[0]), terms(a, p[1]));
      }
    }
  }

  /** The bug: the same stop word written with ё is kept by the default analyzer. */
  public void testStopWordSpelledWithYoIsRemoved() throws Exception {
    Map<String, List<String>> kept = new LinkedHashMap<>();
    try (Analyzer a = new RussianAnalyzer()) {
      for (String[] p : PAIRS) {
        List<String> t = terms(a, p[1]);
        if (t.isEmpty() == false) {
          kept.put(p[1], t);
        }
      }
    }
    assertEquals(
        "default stop words written with ё must be removed like their е spelling (term lists kept)",
        Map.of(),
        kept);
  }

  /** The bug end to end: the same sentence in the two spellings indexes different terms. */
  public void testSameSentenceInBothSpellingsIndexesTheSameTerms() throws Exception {
    try (Analyzer a = new RussianAnalyzer()) {
      // "she is still here": ещё / еще is a default stop word
      assertEquals(terms(a, "она еще здесь"), terms(a, "она ещё здесь"));
    }
  }

  /** The bug in an index: a stop word is indexed (docFreq 1) when written with ё. */
  public void testStopWordWithYoIsNotIndexed() throws Exception {
    try (Directory dir = newDirectory();
        Analyzer a = new RussianAnalyzer()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document d1 = new Document();
        d1.add(new TextField("f", "Она всё ещё здесь, и её брат тоже.", Field.Store.NO));
        w.addDocument(d1);
        Document d2 = new Document();
        d2.add(new TextField("f", "Она все еще здесь, и ее брат тоже.", Field.Store.NO));
        w.addDocument(d2);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Map<String, Integer> df = new LinkedHashMap<>();
        // the terms the analyzer produces for всё, ещё and её (stems of the ё spellings)
        for (String t : new String[] {"все", "ещ", "е"}) {
          int n = r.docFreq(new Term("f", t));
          if (n != 0) {
            df.put(t, n);
          }
        }
        assertEquals("stop words must not be indexed in either spelling (docFreq per term)", Map.of(), df);
      }
    }
  }

  /**
   * The bug in search: a phrase typed with ё ("всё равно", "all the same") does not find a document
   * that writes the same phrase with е, although the same analyzer is used on both sides.
   */
  public void testPhraseWithYoStopWordFindsDocumentSpelledWithIe() throws Exception {
    try (Directory dir = newDirectory();
        Analyzer a = new RussianAnalyzer()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document d = new Document();
        d.add(new TextField("f", "Мне все равно.", Field.Store.NO));
        w.addDocument(d);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        QueryBuilder qb = new QueryBuilder(a);
        Query ie = qb.createPhraseQuery("f", "все равно");
        Query yo = qb.createPhraseQuery("f", "всё равно");
        assertEquals("control: " + ie, 1, s.count(ie));
        assertEquals("same phrase written with ё: " + yo, 1, s.count(yo));
      }
    }
  }
}
