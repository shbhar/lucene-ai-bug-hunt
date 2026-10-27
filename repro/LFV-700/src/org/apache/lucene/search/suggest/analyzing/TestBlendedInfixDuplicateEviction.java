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
package org.apache.lucene.search.suggest.analyzing;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.search.suggest.analyzing.BlendedInfixSuggester.BlenderType;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * BlendedInfixSuggester collapses identical suggestions (same text, blended weight and payload:
 * upstream testBlendedInfixSuggesterDedupsOnWeightTitleAndPayload). When its bounded result set is
 * already full, boundedTreeAdd first evicts the lowest result and only then adds the new one; if
 * the new one is such a duplicate, the add is a no-op, so a distinct suggestion is lost and lookup
 * returns fewer than num suggestions although more exist.
 */
public class TestBlendedInfixDuplicateEviction extends LuceneTestCase {

  private static InputIterator iter(String[] keys, long[] weights) {
    Iterator<Integer> it = java.util.stream.IntStream.range(0, keys.length).iterator();
    return new InputIterator() {
      int cur = -1;

      @Override
      public BytesRef next() {
        if (it.hasNext() == false) {
          return null;
        }
        cur = it.next();
        return new BytesRef(keys[cur]);
      }

      @Override
      public long weight() {
        return weights[cur];
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

  // "the lake" ranks first by raw weight but its match is at position 1, so it is blended down
  // below the (duplicated) exact-prefix suggestion "lake"
  private static final String[] KEYS = {"the lake", "lake", "lake"};
  private static final long[] WEIGHTS = {1000, 950, 950};

  private static List<LookupResult> lookup(BlenderType type, int num) throws Exception {
    return lookup(KEYS, WEIGHTS, type, num);
  }

  private static List<LookupResult> lookup(String[] keys, long[] weights, BlenderType type, int num)
      throws Exception {
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    try (Directory dir = newDirectory();
        BlendedInfixSuggester suggester =
            new BlendedInfixSuggester(
                dir,
                a,
                a,
                AnalyzingInfixSuggester.DEFAULT_MIN_PREFIX_CHARS,
                type,
                BlendedInfixSuggester.DEFAULT_NUM_FACTOR,
                false)) {
      suggester.build(iter(keys, weights));
      return suggester.lookup("lake", false, num);
    } finally {
      a.close();
    }
  }

  public void testLinearReturnsBothDistinctSuggestions() throws Exception {
    // control: with room for three results both distinct suggestions are returned
    assertEquals("[lake/950, the lake/900]", lookup(BlenderType.POSITION_LINEAR, 3).toString());
    assertEquals("[lake/950, the lake/900]", lookup(BlenderType.POSITION_LINEAR, 2).toString());
  }

  public void testReciprocalReturnsBothDistinctSuggestions() throws Exception {
    assertEquals("[lake/950, the lake/500]", lookup(BlenderType.POSITION_RECIPROCAL, 3).toString());
    assertEquals("[lake/950, the lake/500]", lookup(BlenderType.POSITION_RECIPROCAL, 2).toString());
  }

  /** A lower-ranked suggestion takes the slot of the evicted one: wrong result, not only fewer. */
  public void testLowerSuggestionTakesTheEvictedSlot() throws Exception {
    String[] keys = {"the lake", "lake", "lake", "my lake"};
    long[] weights = {1000, 950, 950, 920};
    // blended (POSITION_LINEAR): the lake 900, lake 950, lake 950, my lake 828
    assertEquals(
        "[lake/950, the lake/900, my lake/828]",
        lookup(keys, weights, BlenderType.POSITION_LINEAR, 3).toString());
    assertEquals(
        "[lake/950, the lake/900]", lookup(keys, weights, BlenderType.POSITION_LINEAR, 2).toString());
  }

  /** Control: AnalyzingInfixSuggester (no blending, no dedup) returns two results for num=2. */
  public void testAnalyzingInfixControl() throws Exception {
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    try (Directory dir = newDirectory();
        AnalyzingInfixSuggester suggester = new AnalyzingInfixSuggester(dir, a)) {
      suggester.build(iter(KEYS, WEIGHTS));
      assertEquals(2, suggester.lookup("lake", false, 2).size());
    } finally {
      a.close();
    }
  }
}
