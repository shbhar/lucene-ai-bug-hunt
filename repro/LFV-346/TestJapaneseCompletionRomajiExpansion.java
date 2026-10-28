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
package org.apache.lucene.analysis.ja;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.miscellaneous.LimitTokenCountFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * JapaneseCompletionFilter adds one romanized token per combination of romaji alternatives of a
 * token's reading. KatakanaRomanizer#romanize builds that full combination list eagerly, so a
 * reading with n ambiguous kana (for example n x "ン", which maps to "n" or "nn") yields 2^n
 * romanized tokens, most of them duplicates. A 32-character input needs about 2^32 token objects
 * and exhausts any heap inside the first incrementToken() call.
 *
 * <p>Uses only public API: JapaneseCompletionAnalyzer, JapaneseTokenizer, JapaneseCompletionFilter,
 * LimitTokenCountFilter and IndexWriter.
 */
public class TestJapaneseCompletionRomajiExpansion extends LuceneTestCase {

  private static final int N = 32;

  /** Consumes a token stream; returns {all tokens, romanized tokens at the first position}. */
  private static List<String> romajiOfFirstToken(Analyzer a, String text, long[] total)
      throws IOException {
    List<String> romaji = new ArrayList<>();
    long n = 0;
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      PositionIncrementAttribute posInc = ts.addAttribute(PositionIncrementAttribute.class);
      ts.reset();
      int pos = -1;
      while (ts.incrementToken()) {
        pos += posInc.getPositionIncrement();
        if (pos == 0 && posInc.getPositionIncrement() == 0) {
          romaji.add(term.toString());
        }
        n++;
      }
      ts.end();
    }
    total[0] = n;
    return romaji;
  }

  /** Runs one analysis and turns an OutOfMemoryError into a test failure with a clear message. */
  private static long countTokensOrFail(String what, Analyzer a, String text) throws IOException {
    long start = System.nanoTime();
    long n = 0;
    try (TokenStream ts = a.tokenStream("f", text)) {
      ts.reset();
      while (ts.incrementToken()) {
        n++;
      }
      ts.end();
    } catch (OutOfMemoryError e) {
      long ms = (System.nanoTime() - start) / 1_000_000;
      throw new AssertionError(
          what
              + ": analyzing a "
              + text.length()
              + "-character input threw OutOfMemoryError after "
              + ms
              + " ms and "
              + n
              + " tokens (max heap "
              + (Runtime.getRuntime().maxMemory() >> 20)
              + " MB): "
              + e.getMessage(),
          e);
    }
    return n;
  }

  /**
   * The romanized variants of one token are the distinct keystroke patterns ("shin", "shinn",
   * "sin", "sinn" in the KatakanaRomanizer comment). For "ン" x 8 there are 9 distinct patterns
   * ("n" x 8 .. "n" x 16) but the filter emits 256 romanized tokens at that position.
   */
  public void testRomanizedVariantsAreDistinct() throws IOException {
    StringBuilder growth = new StringBuilder();
    try (Analyzer a = new JapaneseCompletionAnalyzer()) {
      for (int n = 2; n <= 16; n += 2) {
        long[] total = new long[1];
        List<String> romaji = romajiOfFirstToken(a, "ン".repeat(n), total);
        growth
            .append(" n=")
            .append(n)
            .append(":tokens=")
            .append(total[0])
            .append(",distinct=")
            .append(new HashSet<>(romaji).size());
      }
      long[] total = new long[1];
      List<String> romaji = romajiOfFirstToken(a, "ン".repeat(8), total);
      assertEquals(
          "duplicate romanized tokens at one position; growth for \"ン\" x n:" + growth,
          new HashSet<>(romaji).size(),
          romaji.size());
    }
  }

  /** INDEX mode (the JapaneseCompletionAnalyzer default) on a 32-character katakana run. */
  public void testShortKatakanaRunDoesNotExhaustHeapIndexMode() throws IOException {
    try (Analyzer a = new JapaneseCompletionAnalyzer()) {
      long n = countTokensOrFail("INDEX mode, \"ン\" x " + N, a, "ン".repeat(N));
      assertTrue(n > 0);
    }
  }

  /**
   * QUERY mode concatenates consecutive kana tokens before romanizing them, so 32 hiragana "し"
   * (each "し" is "si" or "shi") become one reading with 2^32 romanizations.
   */
  public void testShortHiraganaQueryDoesNotExhaustHeapQueryMode() throws IOException {
    try (Analyzer a = new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.QUERY)) {
      long n = countTokensOrFail("QUERY mode, \"し\" x " + N, a, "し".repeat(N));
      assertTrue(n > 0);
    }
  }

  /**
   * LimitTokenCountFilter, Lucene's own bound on the number of tokens, cannot help: the whole
   * combination list is built inside the first incrementToken() call of JapaneseCompletionFilter.
   */
  public void testLimitTokenCountFilterCannotBoundExpansion() throws IOException {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new JapaneseTokenizer(null, true, true, JapaneseTokenizer.Mode.NORMAL);
            TokenStream s = new JapaneseCompletionFilter(t, JapaneseCompletionFilter.Mode.INDEX);
            return new TokenStreamComponents(t, new LimitTokenCountFilter(s, 10));
          }
        };
    try {
      long n = countTokensOrFail("LimitTokenCountFilter(10) after the filter", a, "ン".repeat(N));
      assertTrue("at most 10 tokens expected, got " + n, n <= 10);
    } finally {
      a.close();
    }
  }

  /**
   * Indexing one document with a 32-character field must not close the IndexWriter. On a tragic
   * Error the writer rolls back, losing every uncommitted document.
   */
  public void testIndexWriterSurvivesShortKatakanaRun() throws IOException {
    try (Directory dir = newDirectory();
        Analyzer a = new JapaneseCompletionAnalyzer()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a));
      Document ok = new Document();
      ok.add(new TextField("title", "東京タワー", Field.Store.NO));
      w.addDocument(ok);
      Document doc = new Document();
      doc.add(new TextField("title", "ン".repeat(N), Field.Store.NO));
      Throwable thrown = null;
      try {
        w.addDocument(doc);
      } catch (Throwable t) {
        thrown = t;
      }
      Throwable tragedy = w.getTragicException();
      boolean open = w.isOpen();
      if (open) {
        w.close();
      }
      // nothing was committed before the failure: if the writer rolled back, no commit exists, so
      // the first (valid) document is gone as well
      boolean anyCommit = DirectoryReader.indexExists(dir);
      assertTrue(
          "IndexWriter open="
              + open
              + " tragic="
              + tragedy
              + " after addDocument of one "
              + N
              + "-character field (addDocument threw "
              + thrown
              + "); index has a commit afterwards: "
              + anyCommit,
          open && tragedy == null);
    }
  }
}
