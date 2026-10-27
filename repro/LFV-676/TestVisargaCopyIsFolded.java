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
package org.apache.lucene.analysis.bn;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * Independent check: BengaliNormalizationFilter geminates a medial visarga by copying the next
 * character, but the copy is taken before that character is folded, so SHA / SSA / NNA survive in
 * the copied position even though the same filter folds them everywhere else.
 *
 * <p>Public API only: BengaliNormalizationFilter, BengaliAnalyzer, IndexWriter, QueryBuilder.
 */
public class TestVisargaCopyIsFolded extends BaseTokenStreamTestCase {

  // NI + I-kar + VISARGA + {SHA | SSA | SA} + BA + HASANTA + DA  ("silent", three spellings)
  private static final String NIH_SHA = "নিঃশব্দ";
  private static final String NIH_SSA = "নিঃষব্দ";
  private static final String NIH_SA = "নিঃসব্দ";
  // what the SA spelling yields: visarga becomes SA, SA stays SA
  private static final String FOLDED = "নিসসব্দ";

  private static String esc(String s) {
    StringBuilder b = new StringBuilder();
    for (char c : s.toCharArray()) {
      b.append(String.format("U+%04X ", (int) c));
    }
    return b.toString().trim();
  }

  private void checkFilter(String input, String expected) throws IOException {
    Tokenizer tok = whitespaceMockTokenizer(input);
    TokenFilter tf = new BengaliNormalizationFilter(tok);
    assertTokenStreamContents(tf, new String[] {expected});
  }

  /** Runs one whole-input token through BengaliNormalizationFilter and returns the term. */
  private static String filterOut(String input) throws IOException {
    Tokenizer t = new org.apache.lucene.analysis.core.KeywordTokenizer();
    t.setReader(new java.io.StringReader(input));
    try (TokenStream ts = new BengaliNormalizationFilter(t)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      assertTrue(ts.incrementToken());
      String out = term.toString();
      assertFalse(ts.incrementToken());
      ts.end();
      return out;
    }
  }

  // ---- controls (expected to pass on stock code) ----

  /** Upstream example: visarga before an unfolded consonant (KHA) geminates it. */
  public void testControlUpstreamVisarga() throws IOException {
    checkFilter("দুঃখ", "দুখখ");
  }

  /**
   * Upstream example (TestBengaliNormalizer.testBaPhalaa): the Ba-phala gemination copies the
   * previous, already folded consonant, so SHA + hasanta + BA gives SA SA, not SHA SA.
   */
  public void testControlBaPhalaGeminationCopiesFoldedValue() throws IOException {
    checkFilter("বিশ্ব", "বিসস");
  }

  /** Without a visarga, SHA / SSA / SA spellings normalize to the same term. */
  public void testControlNoVisargaSpellingsAgree() throws IOException {
    String sha = filterOut("শব্দ");
    String ssa = filterOut("ষব্দ");
    String sa = filterOut("সব্দ");
    assertEquals(esc(sa), esc(sha));
    assertEquals(esc(sa), esc(ssa));
  }

  // ---- claim (expected to fail on stock code) ----

  /** SHA after a medial visarga: the copied position must carry the folded SA. */
  public void testVisargaThenShaFolded() throws IOException {
    String out = filterOut(NIH_SHA);
    assertEquals(esc(FOLDED), esc(out));
  }

  /** SSA after a medial visarga. */
  public void testVisargaThenSsaFolded() throws IOException {
    String out = filterOut(NIH_SSA);
    assertEquals(esc(FOLDED), esc(out));
  }

  /** The SA spelling (sanity: this one is already right). */
  public void testVisargaThenSaFolded() throws IOException {
    checkFilter(NIH_SA, FOLDED);
  }

  /** NNA after a medial visarga: "check na" folds NNA to NA everywhere else. */
  public void testVisargaThenNnaFolded() throws IOException {
    // DA + U-kar + VISARGA + NNA + AA-kar  vs the NA spelling
    String nna = filterOut("দুঃণা");
    String na = filterOut("দুঃনা");
    assertEquals(esc(na), esc(nna));
  }

  /** No SHA or SSA may survive the filter, since the "All sh" rule folds both unconditionally. */
  public void testNoShaOrSsaSurvives() throws IOException {
    for (String in : new String[] {NIH_SHA, NIH_SSA}) {
      String out = filterOut(in);
      assertTrue(
          "input " + esc(in) + " -> " + esc(out),
          out.indexOf('শ') < 0 && out.indexOf('ষ') < 0);
    }
  }

  /**
   * End to end through the stock BengaliAnalyzer: a document spelled with SHA is not found by the
   * SA spelling (and vice versa), although the analyzer conflates SHA and SA in every other
   * position.
   */
  public void testBengaliAnalyzerSearch() throws IOException {
    Analyzer analyzer = new BengaliAnalyzer();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document d = new Document();
        d.add(new TextField("f", NIH_SHA, Field.Store.NO));
        w.addDocument(d);
        Document d2 = new Document();
        // control: SHA without visarga, same word stem
        d2.add(new TextField("g", "শব্দ", Field.Store.NO));
        w.addDocument(d2);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        QueryBuilder qb = new QueryBuilder(analyzer);
        Query control = qb.createBooleanQuery("g", "সব্দ");
        assertEquals("control: SA spelling finds SHA spelling", 1, s.count(control));
        Query q = qb.createBooleanQuery("f", NIH_SA);
        assertEquals(
            "SA spelling should find the SHA-spelled document; analyzer terms: "
                + esc(analyzer.normalize("f", NIH_SHA).utf8ToString())
                + " vs "
                + esc(analyzer.normalize("f", NIH_SA).utf8ToString()),
            1,
            s.count(q));
      }
    } finally {
      analyzer.close();
    }
  }
}
