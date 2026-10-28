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
package org.egothor.stemmer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.lucene.analysis.pl.PolishAnalyzer;
import org.apache.lucene.analysis.stempel.StempelStemmer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Optimizer2 joins only rows whose shared cells are identical; its javadoc says this allows "the
 * stemmer to recognize words for which the original trie was built". These tests apply it to a
 * trie that is already reduced (a second pass) and check that the stored keys keep their answers.
 * Same package as TestStemmer, because Trie.add is package-private.
 */
public class TestRepeatedOptimizer2 extends LuceneTestCase {

  private static String str(CharSequence cs) {
    return cs == null ? null : cs.toString();
  }

  /** Control: one pass on a one-key backward trie keeps the key. */
  public void testOnePassOneKeyControl() {
    Trie t = new Trie(false);
    t.add("aab", "Da");
    Trie once = t.reduce(new Optimizer2());
    assertEquals("Da", str(once.getLastOnPath("aab")));
    assertEquals("Da", str(once.getFully("aab")));
  }

  /** Second Optimizer2 pass on the same one-key trie. */
  public void testTwoPassesOneKey() {
    Trie t = new Trie(false);
    t.add("aab", "Da");
    Trie once = t.reduce(new Optimizer2());
    Trie twice = once.reduce(new Optimizer2());
    assertEquals("getLastOnPath after second pass", "Da", str(twice.getLastOnPath("aab")));
    assertEquals("getFully after second pass", "Da", str(twice.getFully("aab")));
  }

  private static final String[] CMDS = {"Da", "Db", "Rx", "Iy", "Dc"};

  private static Map<String, String> randomKeys(Random r) {
    Map<String, String> m = new LinkedHashMap<>();
    int n = 1 + r.nextInt(6);
    for (int k = 0; k < n; k++) {
      int len = 1 + r.nextInt(4);
      StringBuilder sb = new StringBuilder();
      for (int c = 0; c < len; c++) {
        sb.append((char) ('a' + r.nextInt(3)));
      }
      m.put(sb.toString(), CMDS[r.nextInt(CMDS.length)]); // last add wins, as in Trie.add
    }
    return m;
  }

  private static Trie build(boolean forward, Map<String, String> keys) {
    Trie t = new Trie(forward);
    for (Map.Entry<String, String> e : keys.entrySet()) {
      t.add(e.getKey(), e.getValue());
    }
    return t;
  }

  /** returns {violating tries, violating keys, total keys}; fills one example */
  private int[] check(int passes, StringBuilder example) {
    return check(passes, false, example);
  }

  private int[] check(int passes, boolean useOptimizer, StringBuilder example) {
    Random r = new Random(random().nextLong());
    int badTries = 0, badKeys = 0, total = 0;
    for (int iter = 0; iter < 2000; iter++) {
      boolean forward = r.nextBoolean();
      Map<String, String> keys = randomKeys(r);
      Trie t = build(forward, keys);
      // sanity: the unreduced trie answers every key
      for (Map.Entry<String, String> e : keys.entrySet()) {
        assertEquals(e.getValue(), str(t.getLastOnPath(e.getKey())));
      }
      Trie red = t;
      for (int p = 0; p < passes; p++) {
        red = red.reduce(useOptimizer ? new Optimizer() : new Optimizer2());
      }
      boolean bad = false;
      for (Map.Entry<String, String> e : keys.entrySet()) {
        total++;
        String got = str(red.getLastOnPath(e.getKey()));
        if (e.getValue().equals(got) == false) {
          badKeys++;
          bad = true;
          if (example.length() == 0) {
            example
                .append(forward ? "forward " : "backward ")
                .append(keys)
                .append(": key ")
                .append(e.getKey())
                .append(" expected ")
                .append(e.getValue())
                .append(" got ")
                .append(got);
          }
        }
      }
      if (bad) {
        badTries++;
      }
    }
    return new int[] {badTries, badKeys, total};
  }

  /** Control: a single Optimizer2 pass keeps every stored key of 2000 random small tries. */
  public void testRandomTriesOnePassControl() {
    StringBuilder ex = new StringBuilder();
    int[] res = check(1, ex);
    assertEquals("one pass: " + res[1] + " of " + res[2] + " keys lost, e.g. " + ex, 0, res[1]);
  }

  /** Two Optimizer2 passes on the same 2000 random small tries. */
  public void testRandomTriesTwoPasses() {
    StringBuilder ex = new StringBuilder();
    int[] res = check(2, ex);
    assertEquals(
        "two passes: "
            + res[0]
            + " of 2000 tries and "
            + res[1]
            + " of "
            + res[2]
            + " keys lost, e.g. "
            + ex,
        0,
        res[1]);
  }

  /**
   * Same with Optimizer (the inherited optimize() is the same code). Optimizer documents some
   * information loss, so this control only records whether one pass keeps these small tries.
   */
  public void testRandomTriesOptimizerOnePassControl() {
    StringBuilder ex = new StringBuilder();
    int[] res = check(1, true, ex);
    assertEquals("Optimizer one pass: " + res[1] + " of " + res[2] + " keys lost, e.g. " + ex, 0, res[1]);
  }

  public void testRandomTriesOptimizerTwoPasses() {
    StringBuilder ex = new StringBuilder();
    int[] res = check(2, true, ex);
    assertEquals(
        "Optimizer two passes: " + res[0] + " of 2000 tries and " + res[1] + " of " + res[2]
            + " keys lost, e.g. " + ex,
        0,
        res[1]);
  }

  // ---- public API only: the Compile tool and StempelStemmer ----

  private static final String TABLE =
      "aa aab\n"
          + "walk walks walked walking\n"
          + "jump jumps jumped jumping\n"
          + "stem stems stemmed stemming\n"
          + "run runs running\n"
          + "talk talks talked talking\n";

  private List<String> compileAndStem(String method) throws Exception {
    Path dir = createTempDir("v1compile");
    Path table = dir.resolve("table.txt");
    Files.write(table, TABLE.getBytes(StandardCharsets.UTF_8));
    Compile.main(new String[] {method, table.toAbsolutePath().toString()});
    Trie trie;
    try (InputStream in = Files.newInputStream(dir.resolve("table.txt.out"))) {
      trie = StempelStemmer.load(in);
    }
    StempelStemmer stemmer = new StempelStemmer(trie);
    List<String> wrong = new ArrayList<>();
    for (String line : TABLE.split("\n")) {
      String[] tok = line.split(" ");
      for (int i = 1; i < tok.length; i++) {
        StringBuilder s = stemmer.stem(tok[i]);
        String got = s == null ? null : s.toString();
        if (tok[0].equals(got) == false) {
          wrong.add(tok[i] + "->" + got + " (expected " + tok[0] + ")");
        }
      }
    }
    return wrong;
  }

  /** Control: Compile with one Optimizer2 pass stems every training form to its stem. */
  public void testCompileOnePassControl() throws Exception {
    assertEquals("-2", List.of(), compileAndStem("-2"));
    assertEquals("-M2", List.of(), compileAndStem("-M2"));
  }

  /** Compile with two Optimizer2 passes (method -22, backward trie). */
  public void testCompileTwoPasses() throws Exception {
    assertEquals("-22", List.of(), compileAndStem("-22"));
  }

  /** Compile with two Optimizer2 passes on a MultiTrie2 (method -M22). */
  public void testCompileTwoPassesMulti() throws Exception {
    assertEquals("-M22", List.of(), compileAndStem("-M22"));
  }

  // ---- bundled Polish table: a second Optimizer2 pass must keep every stored path ----

  private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyząćęłńóśźż";

  /**
   * Collects keys whose whole walk exists in t (every character finds a cell, the last cell has a
   * command). A correct Optimizer2 keeps such walks cell by cell, so it keeps their answers.
   */
  private static void collect(
      Trie t, int rowIdx, StringBuilder path, int depth, int cap, List<String> out) {
    if (out.size() >= cap || depth == 0) {
      return;
    }
    Row row = t.rows.get(rowIdx);
    for (int a = 0; a < ALPHABET.length() && out.size() < cap; a++) {
      char ch = ALPHABET.charAt(a);
      Cell c = row.at(ch);
      if (c == null) {
        continue;
      }
      path.append(ch);
      if (c.cmd >= 0) {
        String p = path.toString();
        out.add(t.forward ? p : new StringBuilder(p).reverse().toString());
      }
      if (c.ref >= 0) {
        collect(t, c.ref, path, depth - 1, cap, out);
      }
      path.setLength(path.length() - 1);
    }
  }

  public void testBundledTableSecondPassKeepsStoredPaths() throws Exception {
    Trie table;
    try (InputStream in =
        PolishAnalyzer.class.getResourceAsStream(PolishAnalyzer.DEFAULT_STEMMER_FILE)) {
      table = StempelStemmer.load(in);
    }
    assertTrue(table instanceof MultiTrie);
    List<Trie> subs = ((MultiTrie) table).tries;
    int checked = 0, lost = 0, lowerRefs = 0;
    String example = null;
    for (int s = 0; s < subs.size(); s++) {
      Trie t = subs.get(s);
      for (int ri = 0; ri < t.rows.size(); ri++) {
        Row row = t.rows.get(ri);
        for (int a = 0; a < ALPHABET.length(); a++) {
          Cell c = row.at(ALPHABET.charAt(a));
          if (c != null && c.ref >= 0 && c.ref <= ri) {
            lowerRefs++;
          }
        }
      }
      List<String> keys = new ArrayList<>();
      collect(t, t.root, new StringBuilder(), 8, 5000, keys);
      Trie red = t.reduce(new Optimizer2());
      for (String k : keys) {
        checked++;
        String before = str(t.getLastOnPath(k));
        String after = str(red.getLastOnPath(k));
        if (before.equals(after) == false) {
          lost++;
          if (example == null) {
            example = "subtrie " + s + " key " + k + ": " + before + " -> " + after;
          }
        }
      }
    }
    System.out.println(
        "bundled table: sub-tries="
            + subs.size()
            + " refs to same-or-lower row (alphabet cells)="
            + lowerRefs
            + " stored-path keys checked="
            + checked
            + " changed="
            + lost);
    assertEquals(
        lost + " of " + checked + " stored-path keys changed, e.g. " + example, 0, lost);
  }
}
