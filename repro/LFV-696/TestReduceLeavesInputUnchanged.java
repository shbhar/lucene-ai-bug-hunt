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
package org.apache.lucene.analysis.pl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.stempel.StempelStemmer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.egothor.stemmer.Gener;
import org.egothor.stemmer.Lift;
import org.egothor.stemmer.MultiTrie;
import org.egothor.stemmer.Optimizer;
import org.egothor.stemmer.Optimizer2;
import org.egothor.stemmer.Reduce;
import org.egothor.stemmer.Trie;

/**
 * Trie.reduce(Reduce) documents that it returns "the newly reduced Trie", Gener.optimize documents
 * "a new optimized Trie", and PolishAnalyzer.getDefaultTable() documents an "unmodifiable
 * instance". Reducing a trie must therefore leave the answers of the input trie unchanged. Only
 * public API is used (different package from org.egothor.stemmer).
 */
public class TestReduceLeavesInputUnchanged extends LuceneTestCase {

  private static final String[] WORDS = {
    "książkami", "rzekami", "pracowali", "dzieciom", "domach", "miastach", "kobietami",
    "pisałem", "czytała", "zielonymi", "najlepszych", "chodziliśmy", "biegnącego", "oknami",
    "ludziach", "rozmawiać", "nauczycielowi", "pięknego", "szkołach", "studentów"
  };

  private static final String ALPHA = "aąbcćdeęfghijklłmnńoóprsśtuwyzźż";

  // ---- small tries built only through public API (MultiTrie.add is public) ----

  /** Twenty one-letter keys in one row: Gener drops every command of that row. */
  public void testGenerSmallTrie() {
    MultiTrie t = new MultiTrie(true);
    for (int i = 0; i < 20; i++) {
      t.add(String.valueOf((char) ('a' + i)), "X");
    }
    assertEquals("X", t.getLastOnPath("a").toString());
    assertEquals("X", t.getFully("a").toString());
    Trie reduced = t.reduce(new Gener());
    assertNotSame(t, reduced);
    // the input must still answer as before
    assertEquals("input getFully(a) after reduce(Gener)", "X", t.getFully("a").toString());
    assertEquals("input getLastOnPath(a) after reduce(Gener)", "X", t.getLastOnPath("a").toString());
  }

  /** One two-letter key: Lift(false) lifts the command of "ab" into the cell of "a". */
  public void testLiftFalseSmallTrie() {
    MultiTrie t = new MultiTrie(true);
    t.add("ab", "X");
    assertEquals("X", t.getFully("ab").toString());
    assertEquals("", t.getLastOnPath("a").toString());
    Trie reduced = t.reduce(new Lift(false));
    assertNotSame(t, reduced);
    assertEquals("input getFully(ab) after reduce(Lift(false))", "X", t.getFully("ab").toString());
    assertEquals(
        "input getLastOnPath(a) after reduce(Lift(false))", "", t.getLastOnPath("a").toString());
  }

  /** Same key, Lift(true). */
  public void testLiftTrueSmallTrie() {
    MultiTrie t = new MultiTrie(true);
    t.add("ab", "X");
    assertEquals("", t.getLastOnPath("a").toString());
    t.reduce(new Lift(true));
    assertEquals(
        "input getLastOnPath(a) after reduce(Lift(true))", "", t.getLastOnPath("a").toString());
  }

  // ---- fresh private copies of the bundled Polish table ----

  private static Trie loadBundledCopy() throws IOException {
    InputStream in = PolishAnalyzer.class.getResourceAsStream(PolishAnalyzer.DEFAULT_STEMMER_FILE);
    assertNotNull(in);
    return StempelStemmer.load(in);
  }

  private static List<String> probes() {
    List<String> p = new ArrayList<>();
    for (String w : WORDS) {
      p.add(w);
    }
    for (int a = 0; a < ALPHA.length(); a++) {
      String s1 = "" + ALPHA.charAt(a);
      p.add(s1);
      p.add("kot" + s1);
      for (int b = 0; b < ALPHA.length(); b++) {
        String s2 = s1 + ALPHA.charAt(b);
        p.add(s2);
        p.add("prac" + s2);
        for (int c = 0; c < ALPHA.length(); c++) {
          p.add(s2 + ALPHA.charAt(c));
          p.add("dom" + s2 + ALPHA.charAt(c));
        }
      }
    }
    return p;
  }

  private static List<String> answers(Trie t, List<String> probes) {
    List<String> out = new ArrayList<>(probes.size());
    for (String k : probes) {
      CharSequence r = t.getLastOnPath(k);
      out.add(r == null ? null : r.toString());
    }
    return out;
  }

  private static void checkBundledCopy(Reduce by, String name) throws IOException {
    Trie t = loadBundledCopy();
    List<String> probes = probes();
    List<String> before = answers(t, probes);
    Trie reduced = t.reduce(by);
    assertNotSame(t, reduced);
    List<String> after = answers(t, probes);
    int diffs = 0;
    String first = null;
    for (int i = 0; i < probes.size(); i++) {
      if (!java.util.Objects.equals(before.get(i), after.get(i))) {
        if (first == null) {
          first = probes.get(i) + ": " + before.get(i) + " -> " + after.get(i);
        }
        diffs++;
      }
    }
    assertEquals(
        "input answers changed by reduce("
            + name
            + ") in "
            + diffs
            + "/"
            + probes.size()
            + " probes, first: "
            + first,
        0,
        diffs);
  }

  public void testBundledCopyLiftTrue() throws IOException {
    checkBundledCopy(new Lift(true), "Lift(true)");
  }

  public void testBundledCopyLiftFalse() throws IOException {
    checkBundledCopy(new Lift(false), "Lift(false)");
  }

  public void testBundledCopyGener() throws IOException {
    checkBundledCopy(new Gener(), "Gener");
  }

  /** Control: Optimizer copies cells and must pass. */
  public void testBundledCopyOptimizerControl() throws IOException {
    checkBundledCopy(new Optimizer(), "Optimizer");
  }

  /** Control: Optimizer2 copies cells and must pass. */
  public void testBundledCopyOptimizer2Control() throws IOException {
    checkBundledCopy(new Optimizer2(), "Optimizer2");
  }

  // ---- the shared default table ----

  private static List<String> analyze(Analyzer a, String text) throws IOException {
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

  /**
   * The only test that touches the JVM-wide default table: reduce it once, discard the result, and
   * check that a new PolishAnalyzer still analyzes the same way.
   */
  public void testDefaultTableAfterReduceGener() throws IOException {
    String text = String.join(" ", WORDS);
    List<String> before;
    try (Analyzer a = new PolishAnalyzer()) {
      before = analyze(a, text);
    }
    Trie table = PolishAnalyzer.getDefaultTable();
    assertSame(table, PolishAnalyzer.getDefaultTable());
    Trie reduced = table.reduce(new Gener());
    assertNotSame(table, reduced);
    List<String> after;
    try (Analyzer a = new PolishAnalyzer()) {
      after = analyze(a, text);
    }
    assertEquals("new PolishAnalyzer output after getDefaultTable().reduce(Gener)", before, after);
  }
}
