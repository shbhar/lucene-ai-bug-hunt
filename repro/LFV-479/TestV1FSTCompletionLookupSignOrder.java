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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Independent check: FSTCompletionLookup must assign buckets that are monotone in the input
 * weight ("sorting the input by weight and then dividing into equal ranges", class javadoc;
 * InputIterator#weight: "higher numbers mean better suggestions"). Uses only public API.
 */
public class TestV1FSTCompletionLookupSignOrder extends LuceneTestCase {

  /** Minimal InputIterator over parallel arrays (no payloads, no contexts). */
  private static final class ArrayInputs implements InputIterator {
    private final String[] keys;
    private final long[] weights;
    private int upto = -1;

    ArrayInputs(String[] keys, long[] weights) {
      this.keys = keys;
      this.weights = weights;
    }

    @Override
    public BytesRef next() throws IOException {
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

  private static String key(int i) {
    return String.format(java.util.Locale.ROOT, "k%02d", i);
  }

  /**
   * Builds a lookup over keys k00..k(n-1) with the given weights and returns null if bucket
   * order is monotone in weight, else a description of the first inversion found.
   */
  private String firstInversion(long[] weights) throws IOException {
    int n = weights.length;
    String[] keys = new String[n];
    for (int i = 0; i < n; i++) {
      keys[i] = key(i);
    }
    try (Directory dir = newDirectory()) {
      FSTCompletionLookup lookup = new FSTCompletionLookup(dir, "v1");
      lookup.build(new ArrayInputs(keys, weights));
      long[] bucket = new long[n];
      for (int i = 0; i < n; i++) {
        Object b = lookup.get(keys[i]);
        assertNotNull("key must be present: " + keys[i], b);
        bucket[i] = (Long) b;
      }
      for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
          if (weights[i] > weights[j] && bucket[i] < bucket[j]) {
            return "weight "
                + weights[i]
                + " got bucket "
                + bucket[i]
                + " but lower weight "
                + weights[j]
                + " got higher bucket "
                + bucket[j];
          }
        }
      }
      return null;
    }
  }

  private static List<String> keysOf(List<LookupResult> results) {
    List<String> out = new ArrayList<>();
    for (LookupResult r : results) {
      out.add(r.key + "/" + r.value);
    }
    return out;
  }

  /** Two entries with weights 10 and -10: the weight-10 entry must be suggested first. */
  public void testMixedSignTopSuggestion() throws IOException {
    try (Directory dir = newDirectory()) {
      FSTCompletionLookup lookup = new FSTCompletionLookup(dir, "v1");
      lookup.build(
          new ArrayInputs(new String[] {"cat", "car"}, new long[] {10, -10}));
      List<LookupResult> res = lookup.lookup("ca", true, 2);
      assertEquals(2, res.size());
      assertEquals(
          "higher weight (cat=10) must rank above car=-10; got " + keysOf(res),
          "cat",
          res.get(0).key.toString());
    }
  }

  /** Extremes accepted by the API: Integer.MAX_VALUE must rank above Integer.MIN_VALUE. */
  public void testIntExtremesTopSuggestion() throws IOException {
    try (Directory dir = newDirectory()) {
      FSTCompletionLookup lookup = new FSTCompletionLookup(dir, "v1");
      lookup.build(
          new ArrayInputs(
              new String[] {"xmax", "xmin"}, new long[] {Integer.MAX_VALUE, Integer.MIN_VALUE}));
      List<LookupResult> res = lookup.lookup("x", true, 2);
      assertEquals(2, res.size());
      assertEquals(
          "MAX_VALUE must rank above MIN_VALUE; got " + keysOf(res), "xmax", res.get(0).key.toString());
    }
  }

  /** 20 distinct weights -10..9: buckets must be monotone in weight. */
  public void testBucketsMonotoneMixedSign() throws IOException {
    long[] w = new long[20];
    for (int i = 0; i < 20; i++) {
      w[i] = i - 10;
    }
    String inv = firstInversion(w);
    assertNull("bucket order inverted: " + inv, inv);
  }

  /** Control: the same 20 relative weights shifted to 0..19 (all non-negative). */
  public void testControlAllNonNegativeMonotone() throws IOException {
    long[] w = new long[20];
    for (int i = 0; i < 20; i++) {
      w[i] = i;
    }
    assertNull(firstInversion(w));
  }

  /** Control: the same 20 relative weights shifted to -20..-1 (all negative). */
  public void testControlAllNegativeMonotone() throws IOException {
    long[] w = new long[20];
    for (int i = 0; i < 20; i++) {
      w[i] = i - 20;
    }
    assertNull(firstInversion(w));
  }

  /** Control: lookups with weights 10 and 5 (both non-negative) rank correctly. */
  public void testControlNonNegativeTopSuggestion() throws IOException {
    try (Directory dir = newDirectory()) {
      FSTCompletionLookup lookup = new FSTCompletionLookup(dir, "v1");
      lookup.build(new ArrayInputs(new String[] {"cat", "car"}, new long[] {10, 5}));
      List<LookupResult> res = lookup.lookup("ca", true, 2);
      assertEquals("cat", res.get(0).key.toString());
    }
  }
}
