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
package org.apache.lucene.analysis.minhash;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: MinHashFilter documents that it keeps "the N lowest values" per hash (bottom-k
 * MinHash). That is a function of the set of input tokens, so repeating a token must not change the
 * output. Only public API is used (MinHashFilter constructor, MinHashFilterFactory via
 * CustomAnalyzer), except testRootCause which uses the package-private FixedSizeTreeSet from the
 * same package, as TestMinHashFilter does.
 */
public class TestMinHashRepeatedInput extends LuceneTestCase {

  private static List<String> minHash(
      String text, int hashCount, int bucketCount, int hashSetSize, boolean withRotation)
      throws IOException {
    Tokenizer tok = new WhitespaceTokenizer();
    tok.setReader(new StringReader(text));
    List<String> out = new ArrayList<>();
    try (TokenStream ts = new MinHashFilter(tok, hashCount, bucketCount, hashSetSize, withRotation)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  private static List<String> terms(Analyzer a, String text) throws IOException {
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

  private static List<String> sorted(List<String> l) {
    List<String> c = new ArrayList<>(l);
    Collections.sort(c);
    return c;
  }

  /** Readable form of emitted min-hash terms (each char is 16 bits of the hash). */
  private static List<String> hex(java.util.Collection<String> terms) {
    List<String> out = new ArrayList<>();
    for (String t : terms) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < t.length(); i++) {
        sb.append(String.format(java.util.Locale.ROOT, "%04x", (int) t.charAt(i)));
      }
      out.add(sb.toString());
    }
    return out;
  }

  /**
   * Oracle: per (hash, bucket), the hashSetSize smallest DISTINCT values among the tokens' hashes.
   * Each token's per-hash value is taken from the filter itself run on that single token
   * (bucketCount=1, hashSetSize=1, which never takes the repeated-value path). String order of the
   * emitted term equals the filter's unsigned (val2, val1) order because the term encodes the hash
   * big-endian in 16-bit chars. Validated below by a control (no repeated token => 0 mismatches).
   */
  private static List<String> expected(
      List<String> tokens, int hashCount, int bucketCount, int hashSetSize) throws IOException {
    int prefix = hashCount > 1 ? 2 : 0;
    long bucketSize = (1L << 32) / bucketCount;
    if ((1L << 32) % bucketCount != 0) {
      bucketSize++;
    }
    Map<String, TreeSet<String>> groups = new TreeMap<>();
    for (String t : new LinkedHashSet<>(tokens)) {
      List<String> perHash = minHash(t, hashCount, 1, 1, false);
      assertEquals(hashCount, perHash.size());
      for (String v : perHash) {
        long top32 = ((long) v.charAt(prefix) << 16) | v.charAt(prefix + 1);
        long bucket = top32 / bucketSize;
        groups.computeIfAbsent(v.substring(0, prefix) + "/" + bucket, x -> new TreeSet<>()).add(v);
      }
    }
    List<String> exp = new ArrayList<>();
    for (TreeSet<String> g : groups.values()) {
      int n = 0;
      for (String v : g) {
        if (n++ == hashSetSize) {
          break;
        }
        exp.add(v);
      }
    }
    Collections.sort(exp);
    return exp;
  }

  private static void allSequences(
      String[] alpha, int maxLen, List<String> cur, List<List<String>> out) {
    if (!cur.isEmpty()) {
      out.add(new ArrayList<>(cur));
    }
    if (cur.size() == maxLen) {
      return;
    }
    for (String a : alpha) {
      cur.add(a);
      allSequences(alpha, maxLen, cur, out);
      cur.remove(cur.size() - 1);
    }
  }

  /** Minimal case: 1 hash, 1 bucket, keep the 2 lowest. */
  public void testMinimal() throws IOException {
    // order two tokens by their hash, using the filter itself
    String p = "a", q = "b";
    if (minHash(p, 1, 1, 1, false).get(0).compareTo(minHash(q, 1, 1, 1, false).get(0)) > 0) {
      String t = p;
      p = q;
      q = t;
    }
    // h(p) < h(q). "q p" keeps both; repeating p must not change that.
    List<String> control = sorted(minHash(q + " " + p, 1, 1, 2, false));
    assertEquals(2, control.size());
    assertEquals(
        "input '" + q + " " + p + " " + p + "' has the same token set as '" + q + " " + p + "'",
        hex(control),
        hex(sorted(minHash(q + " " + p + " " + p, 1, 1, 2, false))));
  }

  /** A repeat can also let a value that is NOT among the k lowest into the signature. */
  public void testWrongValueAdmitted() throws IOException {
    String[] w = {"a", "b", "c"};
    String[] h = new String[3];
    for (int i = 0; i < 3; i++) {
      h[i] = minHash(w[i], 1, 1, 1, false).get(0);
    }
    Integer[] idx = {0, 1, 2};
    java.util.Arrays.sort(idx, (x, y) -> h[x].compareTo(h[y]));
    String x = w[idx[0]], y = w[idx[1]], z = w[idx[2]]; // h(x) < h(y) < h(z)
    String text = x + " " + y + " " + x + " " + z;
    List<String> actual = sorted(minHash(text, 1, 1, 2, false));
    List<String> exp = sorted(List.of(h[idx[0]], h[idx[1]]));
    assertEquals(
        "2 lowest of {" + x + "," + y + "," + z + "} for input '" + text + "'",
        hex(exp),
        hex(actual));
  }

  /**
   * Rules out a multiset reading of "keep the N lowest values": two inputs with the same tokens AND
   * the same repetition counts, only in a different order, must give the same signature.
   */
  public void testSameMultisetDifferentOrder() throws IOException {
    String[] w = {"a", "b", "c"};
    String[] h = new String[3];
    for (int i = 0; i < 3; i++) {
      h[i] = minHash(w[i], 1, 1, 1, false).get(0);
    }
    Integer[] idx = {0, 1, 2};
    java.util.Arrays.sort(idx, (x, y) -> h[x].compareTo(h[y]));
    String x = w[idx[0]], y = w[idx[1]], z = w[idx[2]]; // h(x) < h(y) < h(z)
    String t1 = x + " " + y + " " + x + " " + z;
    String t2 = x + " " + y + " " + z + " " + x;
    assertEquals(
        "'" + t1 + "' vs '" + t2 + "' (same multiset of tokens)",
        hex(sorted(minHash(t2, 1, 1, 2, false))),
        hex(sorted(minHash(t1, 1, 1, 2, false))));
  }

  /** Bounded-exhaustive: every sequence of length 1..5 over 4 tokens, several configurations. */
  public void testExhaustiveSmall() throws IOException {
    String[] alpha = {"a", "b", "c", "d"};
    List<List<String>> seqs = new ArrayList<>();
    allSequences(alpha, 5, new ArrayList<>(), seqs);
    int checked = 0, mmNoRepeat = 0, mmRepeat = 0, mmK1 = 0;
    String firstRepeat = null;
    String firstNoRepeat = null;
    for (int hashCount : new int[] {1, 2}) {
      for (int bucketCount : new int[] {1, 3}) {
        for (int k : new int[] {1, 2, 3}) {
          for (List<String> seq : seqs) {
            String text = String.join(" ", seq);
            List<String> actual = sorted(minHash(text, hashCount, bucketCount, k, false));
            List<String> exp = expected(seq, hashCount, bucketCount, k);
            checked++;
            if (!actual.equals(exp)) {
              boolean repeat = new HashSet<>(seq).size() < seq.size();
              String ex =
                  "'"
                      + text
                      + "' hashCount="
                      + hashCount
                      + " bucketCount="
                      + bucketCount
                      + " hashSetSize="
                      + k
                      + " expected "
                      + exp.size()
                      + " values, got "
                      + actual.size()
                      + (actual.size() == exp.size() ? " (different values)" : "");
              if (k == 1) {
                mmK1++;
              }
              if (repeat) {
                mmRepeat++;
                if (firstRepeat == null) {
                  firstRepeat = ex;
                }
              } else {
                mmNoRepeat++;
                if (firstNoRepeat == null) {
                  firstNoRepeat = ex;
                }
              }
            }
          }
        }
      }
    }
    System.out.println(
        "V1 exhaustive: checked="
            + checked
            + " mismatches(no repeated token)="
            + mmNoRepeat
            + " mismatches(repeated token)="
            + mmRepeat
            + " mismatches(hashSetSize=1)="
            + mmK1
            + " first="
            + firstRepeat);
    assertEquals("ORACLE CONTROL failed (no repeated token): " + firstNoRepeat, 0, mmNoRepeat);
    assertEquals("hashSetSize=1 mismatches", 0, mmK1);
    assertEquals(
        "inputs with a repeated token whose output is not the bottom-k of the token set; first: "
            + firstRepeat,
        0,
        mmRepeat);
  }

  /**
   * The javadoc's own configuration ("1 hash and keep the 100 lowest values") fed with 5-word
   * shingles through the SPI factories. Two texts with exactly the same set of 150 shingles (a
   * 150-word cycle, read once vs twice) must get the same signature.
   */
  public void testJavadocConfigSameShingleSet() throws IOException {
    Analyzer a =
        CustomAnalyzer.builder()
            .withTokenizer("whitespace")
            .addTokenFilter(
                "shingle", "minShingleSize", "5", "maxShingleSize", "5", "outputUnigrams", "false")
            .addTokenFilter(
                "minHash",
                "hashCount",
                "1",
                "bucketCount",
                "1",
                "hashSetSize",
                "100",
                "withRotation",
                "false")
            .build();
    Analyzer shingles =
        CustomAnalyzer.builder()
            .withTokenizer("whitespace")
            .addTokenFilter(
                "shingle", "minShingleSize", "5", "maxShingleSize", "5", "outputUnigrams", "false")
            .build();
    int period = 150;
    StringBuilder once = new StringBuilder();
    StringBuilder twice = new StringBuilder();
    for (int i = 0; i < period + 4; i++) {
      once.append("w").append(i % period).append(' ');
    }
    for (int i = 0; i < 2 * period + 4; i++) {
      twice.append("w").append(i % period).append(' ');
    }
    // precondition: identical shingle sets
    assertEquals(
        new HashSet<>(terms(shingles, once.toString())),
        new HashSet<>(terms(shingles, twice.toString())));
    assertEquals(period, new HashSet<>(terms(shingles, once.toString())).size());

    List<String> sigOnce = sorted(terms(a, once.toString()));
    List<String> sigTwice = sorted(terms(a, twice.toString()));
    assertEquals(100, sigOnce.size());
    TreeSet<String> missing = new TreeSet<>(sigOnce);
    missing.removeAll(sigTwice);
    TreeSet<String> extra = new TreeSet<>(sigTwice);
    extra.removeAll(sigOnce);
    assertTrue(
        "same shingle set, different signature: size "
            + sigTwice.size()
            + ", missing of the 100 lowest: "
            + hex(missing)
            + ", values not among the 100 lowest: "
            + hex(extra),
        sigOnce.equals(sigTwice));
    a.close();
    shingles.close();
  }

  /** Root cause pin (same-package access): adding an element already present shrinks a full set. */
  public void testRootCause() {
    MinHashFilter.FixedSizeTreeSet<Integer> s = new MinHashFilter.FixedSizeTreeSet<>(2);
    assertTrue(s.add(1));
    assertTrue(s.add(2));
    boolean added = s.add(1); // already present; TreeSet.add contract: set unchanged, returns false
    assertFalse(added);
    assertEquals("contents after re-adding an existing element", List.of(1, 2), new ArrayList<>(s));
  }
}
