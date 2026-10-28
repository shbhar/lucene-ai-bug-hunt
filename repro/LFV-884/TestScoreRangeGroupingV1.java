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
package org.apache.lucene.search.grouping;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Grouping by score ranges (DoubleRangeGroupSelector / LongRangeGroupSelector over
 * DoubleValuesSource.SCORES) through the public collector managers. Every test asserts that each
 * hit lands in the range of its real score, as computed by an ordinary scoring search. Uses only
 * public API (the package is the grouping package only for convenience).
 */
public class TestScoreRangeGroupingV1 extends LuceneTestCase {

  private static final Sort BY_ID = new Sort(new SortField(ScoreRangeFixtureV1.ID, SortField.Type.INT));

  private Directory dir;
  private ScoreRangeFixtureV1 fx;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    fx = new ScoreRangeFixtureV1(dir);
    // precondition: the real scores span several ranges, otherwise the test proves nothing
    assertTrue(fx.describeScores(), fx.expectedDouble().size() >= 3);
    assertTrue(fx.describeScores(), fx.expectedLong().size() >= 3);
  }

  @Override
  public void tearDown() throws Exception {
    fx.close();
    dir.close();
    super.tearDown();
  }

  private Supplier<GroupSelector<DoubleRange>> doubleSelector() {
    return () -> new DoubleRangeGroupSelector(DoubleValuesSource.SCORES, fx.doubleFactory());
  }

  private Supplier<GroupSelector<LongRange>> longSelector() {
    return () ->
        new LongRangeGroupSelector(
            DoubleValuesSource.SCORES.toLongValuesSource(), fx.longFactory());
  }

  private <T> Map<String, TreeSet<Integer>> twoPass(
      Supplier<GroupSelector<T>> sel, Sort groupSort, Sort withinGroupSort, boolean maxScores)
      throws IOException {
    Collection<SearchGroup<T>> first =
        fx.searcher.search(
            fx.query, new FirstPassGroupingCollectorManager<>(sel, groupSort, 0, 100));
    Map<String, TreeSet<Integer>> actual = new TreeMap<>();
    if (first == null || first.isEmpty()) {
      return actual;
    }
    TopGroups<T> tg =
        fx.searcher.search(
            fx.query,
            new TopGroupsCollectorManager<>(
                sel, first, groupSort, withinGroupSort, 0, 100, maxScores));
    for (GroupDocs<T> gd : tg.groups) {
      TreeSet<Integer> docs = new TreeSet<>();
      for (ScoreDoc sd : gd.scoreDocs()) {
        docs.add(sd.doc);
      }
      actual.put(String.valueOf(gd.groupValue()), docs);
    }
    return actual;
  }

  /** Control: relevance group sort and relevance within-group sort; every pass scores. */
  public void testControlRelevanceSorts() throws IOException {
    assertEquals(
        fx.describeScores(),
        fx.expectedDouble(),
        twoPass(doubleSelector(), Sort.RELEVANCE, Sort.RELEVANCE, true));
  }

  /** Field group sort, field within-group sort, max scores requested (GroupingSearch default). */
  public void testFieldGroupSortWithMaxScores() throws IOException {
    assertEquals(
        fx.describeScores(), fx.expectedDouble(), twoPass(doubleSelector(), BY_ID, BY_ID, true));
  }

  /** Field group sort, field within-group sort, no max scores: neither pass asks for scores. */
  public void testFieldGroupSortNoMaxScores() throws IOException {
    assertEquals(
        fx.describeScores(), fx.expectedDouble(), twoPass(doubleSelector(), BY_ID, BY_ID, false));
  }

  /** Relevance group sort (first pass scores) but a non-scoring second pass. */
  public void testSecondPassOnly() throws IOException {
    assertEquals(
        fx.describeScores(),
        fx.expectedDouble(),
        twoPass(doubleSelector(), Sort.RELEVANCE, BY_ID, false));
  }

  /** Same as testFieldGroupSortNoMaxScores with the long range selector. */
  public void testLongRangeFieldGroupSort() throws IOException {
    assertEquals(
        fx.describeScores(), fx.expectedLong(), twoPass(longSelector(), BY_ID, BY_ID, false));
  }

  /** AllGroupsCollectorManager on its own must report every range that has a hit. */
  public void testAllGroups() throws IOException {
    Collection<DoubleRange> groups =
        fx.searcher.search(fx.query, new AllGroupsCollectorManager<>(doubleSelector()));
    TreeSet<String> actual = new TreeSet<>();
    for (DoubleRange r : groups) {
      actual.add(String.valueOf(r));
    }
    assertEquals(fx.describeScores(), new TreeSet<>(fx.expectedDouble().keySet()), actual);
  }

  /** AllGroupHeadsCollectorManager with an id within-group sort: one head per range, the min id. */
  public void testAllGroupHeads() throws IOException {
    AllGroupHeadsCollectorManager.GroupHeadsResult res =
        fx.searcher.search(fx.query, new AllGroupHeadsCollectorManager<>(doubleSelector(), BY_ID));
    TreeSet<Integer> actual = new TreeSet<>();
    for (int doc : res.retrieveGroupHeads()) {
      actual.add(doc);
    }
    assertEquals(fx.describeScores(), fx.expectedHeadsDouble(), actual);
  }

  /**
   * Direct check of what the selector reads: a pass-through score source records the value the
   * DoubleRangeGroupSelector gets for every collected doc in a first pass sorted by id; each must be
   * the doc's real score.
   */
  public void testSelectorSeesRealScores() throws IOException {
    final Map<Integer, Double> seen = new TreeMap<>();
    DoubleValuesSource recording =
        new DoubleValuesSource() {
          @Override
          public DoubleValues getValues(LeafReaderContext ctx, DoubleValues scores)
              throws IOException {
            final DoubleValues in = DoubleValuesSource.SCORES.getValues(ctx, scores);
            return new DoubleValues() {
              int doc = -1;

              @Override
              public double doubleValue() throws IOException {
                double v = in.doubleValue();
                seen.put(ctx.docBase + doc, v);
                return v;
              }

              @Override
              public boolean advanceExact(int target) throws IOException {
                doc = target;
                return in.advanceExact(target);
              }
            };
          }

          @Override
          public boolean needsScores() {
            return true;
          }

          @Override
          public DoubleValuesSource rewrite(IndexSearcher searcher) {
            return this;
          }

          @Override
          public int hashCode() {
            return System.identityHashCode(this);
          }

          @Override
          public boolean equals(Object obj) {
            return obj == this;
          }

          @Override
          public String toString() {
            return "recordingScores";
          }

          @Override
          public boolean isCacheable(LeafReaderContext ctx) {
            return false;
          }
        };
    fx.searcher.search(
        fx.query,
        new FirstPassGroupingCollectorManager<DoubleRange>(
            () -> new DoubleRangeGroupSelector(recording, fx.doubleFactory()), BY_ID, 0, 100));
    Map<Integer, Double> expected = new TreeMap<>();
    for (int doc = 0; doc < fx.scoreByDoc.length; doc++) {
      expected.put(doc, (double) fx.scoreByDoc[doc]);
    }
    assertEquals("values read by the group selector", expected, seen);
  }

  /**
   * Mechanism check (expected to pass): the same id order, but with a score tie-break the group
   * sort needs scores, so the first pass asks for them; the groups are then correct. Also prints
   * the score modes the collectors report.
   */
  public void testMechanismScoreTieBreak() throws IOException {
    Sort idThenScore = new Sort(new SortField(ScoreRangeFixtureV1.ID, SortField.Type.INT), SortField.FIELD_SCORE);
    System.out.println(
        "V1 scoreMode: firstPass(BY_ID)="
            + new FirstPassGroupingCollectorManager<>(doubleSelector(), BY_ID, 0, 10)
                .newCollector()
                .scoreMode()
            + " firstPass(idThenScore)="
            + new FirstPassGroupingCollectorManager<>(doubleSelector(), idThenScore, 0, 10)
                .newCollector()
                .scoreMode()
            + " allGroups="
            + new AllGroupsCollectorManager<>(doubleSelector()).newCollector().scoreMode()
            + " allGroupHeads(BY_ID)="
            + new AllGroupHeadsCollectorManager<>(doubleSelector(), BY_ID).newCollector().scoreMode());
    System.out.println("V1 " + fx.describeScores());
    System.out.println("V1 expected=" + fx.expectedDouble());
    System.out.println("V1 actual(BY_ID,BY_ID,false)=" + twoPass(doubleSelector(), BY_ID, BY_ID, false));
    System.out.println("V1 actual(BY_ID,BY_ID,true)=" + twoPass(doubleSelector(), BY_ID, BY_ID, true));
    assertEquals(
        fx.describeScores(),
        fx.expectedDouble(),
        twoPass(doubleSelector(), idThenScore, BY_ID, true));
  }
}
