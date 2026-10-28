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
package org.apache.lucene.search.suggest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.search.suggest.fst.FSTCompletion;
import org.apache.lucene.search.suggest.fst.FSTCompletionBuilder;
import org.apache.lucene.search.suggest.fst.FSTCompletionLookup;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Contract under test (FSTCompletionLookup 4-argument constructor javadoc): "@param exactMatchFirst
 * If true exact matches are promoted to the top of the suggestions list. Otherwise they appear in
 * the order of discretized weight and alphabetical within the bucket."
 *
 * <p>Public API only (this test lives outside the fst package on purpose).
 */
public class TestFSTCompletionLookupExactMatchFlag extends LuceneTestCase {

  /** Minimal InputIterator over parallel arrays (no payloads, no contexts). */
  private static final class ArrayInput implements InputIterator {
    private final String[] keys;
    private final long[] weights;
    private int upto = -1;

    ArrayInput(String[] keys, long[] weights) {
      this.keys = keys;
      this.weights = weights;
    }

    @Override
    public BytesRef next() {
      upto++;
      return upto < keys.length ? new BytesRef(keys[upto]) : null;
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

  // Five distinct weights, ten buckets: bucket = line * 10 / 5 over the weight-ascending order,
  // so foo -> 0, bar -> 2, fool -> 4, food -> 6, foobar -> 8.
  private static final String[] KEYS = {"foo", "bar", "fool", "food", "foobar"};
  private static final long[] WEIGHTS = {1, 5, 200, 500, 1000};

  private static String render(List<LookupResult> results) {
    List<String> out = new ArrayList<>();
    for (LookupResult r : results) {
      out.add(r.key + "/" + r.value);
    }
    return out.toString();
  }

  private static FSTCompletionLookup built(Directory dir, boolean exactMatchFirst)
      throws IOException {
    FSTCompletionLookup lookup = new FSTCompletionLookup(dir, "v1", 10, exactMatchFirst);
    lookup.build(new ArrayInput(KEYS, WEIGHTS));
    return lookup;
  }

  /** exactMatchFirst=false, build(): the most popular completion must come first. */
  public void testBuiltLookupWithExactMatchFirstFalseRanksByWeight() throws Exception {
    try (Directory dir = newDirectory()) {
      FSTCompletionLookup lookup = built(dir, false);
      assertEquals(
          "top-1 by weight", "[foobar/8]", render(lookup.lookup("foo", true, 1)));
      assertEquals(
          "full by-weight order",
          "[foobar/8, food/6, fool/4, foo/0]",
          render(lookup.lookup("foo", true, 10)));
    }
  }

  /**
   * exactMatchFirst=false, store() then load() into an instance constructed with
   * exactMatchFirst=false: same documented order.
   */
  public void testLoadedLookupWithExactMatchFirstFalseRanksByWeight() throws Exception {
    try (Directory dir = newDirectory()) {
      FSTCompletionLookup source = built(dir, false);
      ByteBuffersDataOutput out = new ByteBuffersDataOutput();
      assertTrue(source.store(out));

      FSTCompletionLookup loaded = new FSTCompletionLookup(dir, "v1", 10, false);
      assertTrue(loaded.load(new ByteArrayDataInput(out.toArrayCopy())));
      assertEquals(5, loaded.getCount());
      assertEquals(
          "full by-weight order after load",
          "[foobar/8, food/6, fool/4, foo/0]",
          render(loaded.lookup("foo", true, 10)));
    }
  }

  /** Control: exactMatchFirst=true promotes the exact match (documented). Expected to pass. */
  public void testControlExactMatchFirstTrue() throws Exception {
    try (Directory dir = newDirectory()) {
      FSTCompletionLookup lookup = built(dir, true);
      assertEquals(
          "[foo/0, foobar/8, food/6, fool/4]", render(lookup.lookup("foo", true, 10)));
    }
  }

  /** Control: onlyMorePopular=false is alphabetical either way. Expected to pass. */
  public void testControlAlphabeticalUnaffected() throws Exception {
    try (Directory dir = newDirectory()) {
      FSTCompletionLookup lookup = built(dir, false);
      assertEquals(
          "[foo/0, foobar/8, food/6, fool/4]", render(lookup.lookup("foo", false, 10)));
    }
  }

  /**
   * Control: the constructor that takes a pre-built FSTCompletion, same terms and same buckets,
   * honours exactMatchFirst=false. Expected to pass.
   */
  public void testControlPrebuiltCompletionConstructorHonoursFlag() throws Exception {
    // Default builder: 10 buckets, in-memory sorter (portable across 9.x/10.x/main).
    FSTCompletionBuilder builder = new FSTCompletionBuilder();
    int[] buckets = {0, 2, 4, 6, 8};
    for (int i = 0; i < KEYS.length; i++) {
      builder.add(new BytesRef(KEYS[i]), buckets[i]);
    }
    FSTCompletion completion = builder.build();
    try (Directory dir = newDirectory()) {
      FSTCompletionLookup lookup = new FSTCompletionLookup(dir, "v1", completion, false);
      assertEquals(
          "[foobar/8, food/6, fool/4, foo/0]", render(lookup.lookup("foo", true, 10)));
    }
  }

  /**
   * Reference ordering derived only from the lookup's own outputs: take every completion of the
   * key (lookup(key, false, MAX), which reports each completion's bucket), order by bucket
   * descending then by UTF-8 (ASCII here) ascending, optionally pull the exact match to the
   * front, and keep the first num.
   */
  private static List<String> reference(
      FSTCompletionLookup lookup, String key, int num, boolean exactFirst) throws IOException {
    List<LookupResult> all = new ArrayList<>(lookup.lookup(key, false, Integer.MAX_VALUE));
    all.sort(
        Comparator.comparingLong((LookupResult r) -> -r.value)
            .thenComparing(r -> r.key.toString()));
    List<String> keys = new ArrayList<>();
    for (LookupResult r : all) {
      keys.add(r.key + "/" + r.value);
    }
    if (exactFirst) {
      for (int i = 0; i < all.size(); i++) {
        if (all.get(i).key.toString().equals(key)) {
          keys.add(0, keys.remove(i));
          break;
        }
      }
    }
    return keys.subList(0, Math.min(num, keys.size()));
  }

  private static List<String> actual(FSTCompletionLookup lookup, String key, int num)
      throws IOException {
    List<String> keys = new ArrayList<>();
    for (LookupResult r : lookup.lookup(key, true, num)) {
      keys.add(r.key + "/" + r.value);
    }
    return keys;
  }

  /** Counts lookups whose higher-weights-first result deviates from the reference. */
  private long mismatches(boolean exactMatchFirst, long seed, StringBuilder firstExample)
      throws IOException {
    Random rnd = new Random(seed);
    String[] alphabet = {"a", "b"};
    long bad = 0;
    try (Directory dir = newDirectory()) {
      for (int d = 0; d < 300; d++) {
        int n = 2 + rnd.nextInt(7);
        Map<String, Long> dict = new LinkedHashMap<>();
        while (dict.size() < n) {
          int len = 1 + rnd.nextInt(4);
          StringBuilder sb = new StringBuilder();
          for (int i = 0; i < len; i++) {
            sb.append(alphabet[rnd.nextInt(alphabet.length)]);
          }
          dict.putIfAbsent(sb.toString(), (long) rnd.nextInt(21));
        }
        int buckets = new int[] {2, 3, 5, 10}[rnd.nextInt(4)];
        String[] keys = dict.keySet().toArray(new String[0]);
        long[] weights = dict.values().stream().mapToLong(Long::longValue).toArray();
        FSTCompletionLookup lookup =
            new FSTCompletionLookup(dir, "v1r", buckets, exactMatchFirst);
        lookup.build(new ArrayInput(keys, weights));
        for (String key : List.of("a", "b", "aa", "ab", "ba", "bb", "aab", "aba", "bab")) {
          for (int num = 1; num <= n; num++) {
            List<String> want = reference(lookup, key, num, exactMatchFirst);
            List<String> got = actual(lookup, key, num);
            if (!want.equals(got)) {
              if (bad == 0) {
                firstExample
                    .append("dict=")
                    .append(dict)
                    .append(" buckets=")
                    .append(buckets)
                    .append(" key=")
                    .append(key)
                    .append(" num=")
                    .append(num)
                    .append(" expected=")
                    .append(want)
                    .append(" actual=")
                    .append(got)
                    .append(" input=")
                    .append(Arrays.toString(keys));
              }
              bad++;
            }
          }
        }
      }
    }
    return bad;
  }

  /** Bounded random differential, exactMatchFirst=false: expected to fail on affected versions. */
  public void testRandomDifferentialExactMatchFirstFalse() throws Exception {
    StringBuilder ex = new StringBuilder();
    long bad = mismatches(false, random().nextLong(), ex);
    assertEquals("mismatching lookups; first: " + ex, 0, bad);
  }

  /** Same differential with exactMatchFirst=true: validates the oracle. Expected to pass. */
  public void testRandomDifferentialControlExactMatchFirstTrue() throws Exception {
    StringBuilder ex = new StringBuilder();
    long bad = mismatches(true, random().nextLong(), ex);
    assertEquals("mismatching lookups; first: " + ex, 0, bad);
  }
}
