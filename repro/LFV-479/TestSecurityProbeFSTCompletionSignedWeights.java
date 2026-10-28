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
package org.apache.lucene.search.suggest.fst;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.suggest.DocumentDictionary;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Security probe for the FSTCompletionLookup sign-order bug. It asserts only safety properties
 * (no entry outside the dictionary, no entry that does not match the prefix, the same result set
 * as an order-preserving non-negative control, bounded work) and prints PROBE lines that describe
 * the ranking effect. Public API only.
 */
public class TestSecurityProbeFSTCompletionSignedWeights extends LuceneTestCase {

  private static final class ArrayInputs implements InputIterator {
    private final List<String> keys;
    private final long[] weights;
    private int upto = -1;

    ArrayInputs(List<String> keys, long[] weights) {
      this.keys = keys;
      this.weights = weights;
    }

    @Override
    public BytesRef next() {
      upto++;
      return upto < keys.size() ? new BytesRef(keys.get(upto)) : null;
    }

    @Override
    public long weight() {
      return weights[upto];
    }

    @Override
    public BytesRef payload() {
      return null;
    }

    @Override
    public boolean hasPayloads() {
      return false;
    }

    @Override
    public Set<BytesRef> contexts() {
      return null;
    }

    @Override
    public boolean hasContexts() {
      return false;
    }
  }

  private static void probe(String s) {
    System.out.println("PROBE " + s);
  }

  private static List<String> keys(List<LookupResult> results) {
    List<String> out = new ArrayList<>();
    for (LookupResult r : results) {
      out.add(r.key.toString());
    }
    return out;
  }

  private static List<String> keysWithBucket(List<LookupResult> results) {
    List<String> out = new ArrayList<>();
    for (LookupResult r : results) {
      out.add(r.key + "/" + r.value);
    }
    return out;
  }

  /** Order-preserving map of signed weights to their rank (0..distinct-1): all non-negative. */
  private static long[] rankMapped(long[] w) {
    TreeSet<Long> distinct = new TreeSet<>();
    for (long x : w) {
      distinct.add(x);
    }
    TreeMap<Long, Long> rank = new TreeMap<>();
    long r = 0;
    for (long x : distinct) {
      rank.put(x, r++);
    }
    long[] out = new long[w.length];
    for (int i = 0; i < w.length; i++) {
      out[i] = rank.get(w[i]);
    }
    return out;
  }

  /**
   * Index-backed path: the weights come from a signed NumericDocValues field of indexed documents
   * (DocumentDictionary), as an application might do with a net-vote or log-scaled score. An entry
   * the application gave weight -1 (meant to be demoted) is checked for appearing in the top 2.
   */
  public void testDocumentDictionarySignedWeightField() throws IOException {
    String[] terms = {"apple", "apricot", "april", "aphid"};
    long[] weights = {100, 50, 20, -1};
    try (Directory indexDir = newDirectory();
        Directory tempDir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(indexDir, new IndexWriterConfig())) {
        for (int i = 0; i < terms.length; i++) {
          Document d = new Document();
          d.add(new StoredField("s", terms[i]));
          d.add(new NumericDocValuesField("w", weights[i]));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(indexDir)) {
        FSTCompletionLookup lookup = new FSTCompletionLookup(tempDir, "probe");
        lookup.build(new DocumentDictionary(r, "s", "w").getEntryIterator());
        List<LookupResult> top2 = lookup.lookup("ap", true, 2);
        List<LookupResult> all = lookup.lookup("ap", true, 10);
        probe("DOCDICT weights apple=100 apricot=50 april=20 aphid=-1");
        probe("DOCDICT top2=" + keysWithBucket(top2) + " all=" + keysWithBucket(all));
        probe("DOCDICT demotedEntryInTop2=" + keys(top2).contains("aphid"));
        // Safety: every result is a dictionary entry with the prefix; the full set is unchanged.
        assertEquals(Set.of(terms), new HashSet<>(keys(all)));
        assertEquals(4, all.size());
        assertEquals(2, top2.size());
        for (String k : keys(top2)) {
          assertTrue(k, Set.of(terms).contains(k));
        }
      }
    }
  }

  /**
   * Random mixed-sign dictionary vs an order-preserving non-negative control built from the same
   * entries: for every prefix, the complete result set must be identical and equal to the entries
   * with that prefix; top-k must be a subset of those entries. Counts how often top-k differs.
   */
  public void testResultSetsUnchangedOnlyOrderDiffers() throws IOException {
    Random rnd = new Random(0x5EC157L);
    String alphabet = "abcd";
    LinkedHashSet<String> uniq = new LinkedHashSet<>();
    while (uniq.size() < 3000) {
      int len = 1 + rnd.nextInt(6);
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < len; i++) {
        sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
      }
      uniq.add(sb.toString());
    }
    List<String> keys = new ArrayList<>(uniq);
    long[] w = new long[keys.size()];
    for (int i = 0; i < w.length; i++) {
      int c = rnd.nextInt(20);
      w[i] =
          c == 0 ? Integer.MIN_VALUE : c == 1 ? Integer.MAX_VALUE : (long) rnd.nextInt(2001) - 1000;
    }
    long[] control = rankMapped(w);
    try (Directory d1 = newDirectory();
        Directory d2 = newDirectory()) {
      FSTCompletionLookup stock = new FSTCompletionLookup(d1, "probe");
      stock.build(new ArrayInputs(keys, w));
      FSTCompletionLookup ctl = new FSTCompletionLookup(d2, "probe");
      ctl.build(new ArrayInputs(keys, control));
      List<String> prefixes = new ArrayList<>();
      for (int len = 1; len <= 3; len++) {
        int n = 1;
        for (int i = 0; i < len; i++) {
          n *= alphabet.length();
        }
        for (int code = 0; code < n; code++) {
          StringBuilder sb = new StringBuilder();
          int x = code;
          for (int i = 0; i < len; i++) {
            sb.append(alphabet.charAt(x % alphabet.length()));
            x /= alphabet.length();
          }
          prefixes.add(sb.toString());
        }
      }
      int topKDiffers = 0;
      int checked = 0;
      for (String p : prefixes) {
        Set<String> expected = new HashSet<>();
        for (String k : keys) {
          if (k.startsWith(p)) {
            expected.add(k);
          }
        }
        for (boolean hw : new boolean[] {true, false}) {
          Set<String> sAll = new HashSet<>(keys(stock.lookup(p, hw, keys.size() + 1)));
          Set<String> cAll = new HashSet<>(keys(ctl.lookup(p, hw, keys.size() + 1)));
          assertEquals("stock full set, prefix " + p, expected, sAll);
          assertEquals("control full set, prefix " + p, expected, cAll);
          List<String> sTop = keys(stock.lookup(p, hw, 5));
          List<String> cTop = keys(ctl.lookup(p, hw, 5));
          assertEquals(Math.min(5, expected.size()), sTop.size());
          assertTrue(expected.containsAll(sTop));
          if (hw && !sTop.equals(cTop)) {
            topKDiffers++;
          }
          checked++;
        }
      }
      probe(
          String.format(
              Locale.ROOT,
              "SETS entries=%d prefixes=%d lookups=%d fullSetMismatches=0 top5DiffersFromControl(higherWeightsFirst)=%d/%d",
              keys.size(),
              prefixes.size(),
              checked,
              topKDiffers,
              prefixes.size()));
    }
  }

  /**
   * Work bound: build and lookup cost with mixed-sign weights vs the order-preserving control over
   * the same 100,000 entries. Asserts only completion and equal result counts; prints timings and
   * FST sizes.
   */
  public void testBuildAndLookupCostComparable() throws IOException {
    Random rnd = new Random(0xC057L);
    int n = 100_000;
    LinkedHashSet<String> uniq = new LinkedHashSet<>();
    while (uniq.size() < n) {
      int len = 3 + rnd.nextInt(10);
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < len; i++) {
        sb.append((char) ('a' + rnd.nextInt(26)));
      }
      uniq.add(sb.toString());
    }
    List<String> keys = new ArrayList<>(uniq);
    long[] w = new long[n];
    for (int i = 0; i < n; i++) {
      int c = rnd.nextInt(3);
      w[i] =
          c == 0
              ? (long) Integer.MIN_VALUE + rnd.nextInt(1000)
              : c == 1 ? -rnd.nextInt(1_000_000) : rnd.nextInt(1_000_000);
    }
    long[] control = rankMapped(w);
    List<String> queries = new ArrayList<>();
    for (int i = 0; i < 20_000; i++) {
      String k = keys.get(rnd.nextInt(n));
      queries.add(k.substring(0, 1 + rnd.nextInt(Math.min(3, k.length()))));
    }
    long[] results = new long[2];
    long[] buildNs = new long[2];
    long[] lookupNs = new long[2];
    long[] ram = new long[2];
    long[][] weightSets = {w, control};
    for (int round = 0; round < 2; round++) { // round 0 warms up, round 1 is reported
      for (int v = 0; v < 2; v++) {
        try (Directory d = newDirectory()) {
          FSTCompletionLookup l = new FSTCompletionLookup(d, "probe");
          long t0 = System.nanoTime();
          l.build(new ArrayInputs(keys, weightSets[v]));
          long t1 = System.nanoTime();
          long cnt = 0;
          for (String q : queries) {
            cnt += l.lookup(q, true, 10).size();
          }
          long t2 = System.nanoTime();
          buildNs[v] = t1 - t0;
          lookupNs[v] = t2 - t1;
          results[v] = cnt;
          ram[v] = l.ramBytesUsed();
        }
      }
    }
    assertEquals(results[1], results[0]);
    probe(
        String.format(
            Locale.ROOT,
            "COST entries=%d lookups=%d mixedSign: build=%dms lookups=%dms results=%d fstRam=%d | control: build=%dms lookups=%dms results=%d fstRam=%d",
            n,
            queries.size(),
            buildNs[0] / 1_000_000,
            lookupNs[0] / 1_000_000,
            results[0],
            ram[0],
            buildNs[1] / 1_000_000,
            lookupNs[1] / 1_000_000,
            results[1],
            ram[1]));
  }

  /** A stored lookup keeps the inverted buckets after load (until rebuilt); no error either way. */
  public void testStoreLoadKeepsBuckets() throws IOException {
    List<String> keys = List.of("cat", "car", "cab");
    long[] w = {10, -10, 3};
    try (Directory d = newDirectory()) {
      FSTCompletionLookup l = new FSTCompletionLookup(d, "probe");
      l.build(new ArrayInputs(keys, w));
      List<String> before = keysWithBucket(l.lookup("ca", true, 3));
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      assertTrue(l.store(bos));
      FSTCompletionLookup loaded = new FSTCompletionLookup();
      assertTrue(loaded.load(new ByteArrayInputStream(bos.toByteArray())));
      List<String> after = keysWithBucket(loaded.lookup("ca", true, 3));
      probe("STORE weights cat=10 car=-10 cab=3 before=" + before + " afterLoad=" + after);
      assertEquals(before, after);
    }
  }

  /**
   * Context, not this bug: weights outside the int range are rejected at build time with
   * UnsupportedOperationException (encodeWeight), so weight values are already an input the
   * application has to range-check.
   */
  public void testOutOfIntRangeWeightRejected() throws IOException {
    try (Directory d = newDirectory()) {
      FSTCompletionLookup l = new FSTCompletionLookup(d, "probe");
      UnsupportedOperationException e =
          expectThrows(
              UnsupportedOperationException.class,
              () ->
                  l.build(
                      new ArrayInputs(
                          List.of("a", "b"), new long[] {1, (long) Integer.MAX_VALUE + 1})));
      probe("RANGE weight=2^31 build throws " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }
}
