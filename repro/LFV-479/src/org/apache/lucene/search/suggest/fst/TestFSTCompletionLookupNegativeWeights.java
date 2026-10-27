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
import java.util.List;
import java.util.Set;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * FSTCompletionLookup accepts any weight in the int range, and its javadoc says buckets are
 * assigned by "sorting the input by weight and then dividing into equal ranges". The weights are
 * sorted as unsigned big-endian bytes, so a negative weight sorts above every non-negative weight
 * and lands in the highest bucket.
 */
public class TestFSTCompletionLookupNegativeWeights extends LuceneTestCase {

  /** A plain InputIterator over parallel arrays (no payloads, no contexts). */
  private static InputIterator entries(String[] terms, long[] weights) {
    return new InputIterator() {
      int i = -1;

      @Override
      public BytesRef next() {
        return ++i < terms.length ? new BytesRef(terms[i]) : null;
      }

      @Override
      public long weight() {
        return weights[i];
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
    };
  }

  private static List<LookupResult> build(Directory dir, String[] terms, long[] weights)
      throws IOException {
    FSTCompletionLookup lookup = new FSTCompletionLookup(dir, "fst");
    lookup.build(entries(terms, weights));
    return lookup.lookup("ap", true, 2);
  }

  /** Control: with non-negative weights the higher weight comes first. */
  public void testNonNegativeWeightsControl() throws IOException {
    try (Directory dir = newDirectory()) {
      List<LookupResult> results =
          build(dir, new String[] {"apple", "apricot"}, new long[] {10, 5});
      assertEquals("apple", results.get(0).key.toString());
      assertTrue(results.get(0).value > results.get(1).value);
    }
  }

  /** Weight 10 against weight -10: the -10 suggestion is ranked first. */
  public void testNegativeWeightRankedAboveHigherWeight() throws IOException {
    try (Directory dir = newDirectory()) {
      List<LookupResult> results =
          build(dir, new String[] {"apple", "apricot"}, new long[] {10, -10});
      assertEquals(2, results.size());
      assertEquals(
          "apple (weight 10) must rank above apricot (weight -10), got " + results,
          "apple",
          results.get(0).key.toString());
      assertTrue(
          "bucket of weight 10 must be >= bucket of weight -10, got " + results,
          results.get(0).value >= results.get(1).value);
    }
  }
}
