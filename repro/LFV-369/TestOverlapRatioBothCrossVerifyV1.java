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
package org.apache.lucene.spatial.bbox;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.spatial.ShapeValues;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;

/**
 * Independent verification: BBoxOverlapRatioValueSource (via BBoxStrategy#makeOverlapRatioValueSource)
 * when both the query box and the indexed box cross the dateline and their longitude intersection
 * consists of two disjoint arcs.
 *
 * <p>Uses only public API: BBoxStrategy#createIndexableFields, #makeShapeValueSource,
 * #makeOverlapRatioValueSource, DoubleValuesSource#getValues/#explain/#getSortField. The class sits
 * in the bbox package only by convention.
 *
 * <p>Oracle: the class javadoc formula, score = (I/queryArea)*p + (I/targetArea)*(1-p) where I is
 * "the area of the intersection between the query and target envelopes" (degree space, as the
 * class computes areas as width*height in degrees). I is computed independently by intersecting the
 * longitude arcs of the two boxes.
 */
public class TestOverlapRatioBothCrossVerifyV1 extends LuceneTestCase {

  private static final double EPS = 1e-9;
  private final SpatialContext ctx = SpatialContext.GEO;

  private Rectangle rect(double minX, double maxX, double minY, double maxY) {
    return ctx.getShapeFactory().rect(minX, maxX, minY, maxY);
  }

  /** Longitude arcs covered by a box, as [lo, hi] intervals inside [-180, 180]. */
  private static double[][] lonArcs(Rectangle r) {
    if (r.getCrossesDateLine()) {
      return new double[][] {{r.getMinX(), 180}, {-180, r.getMaxX()}};
    }
    return new double[][] {{r.getMinX(), r.getMaxX()}};
  }

  /** Total length of the intersection of the two boxes' longitude coverage. */
  private static double lonIntersection(Rectangle a, Rectangle b) {
    double w = 0;
    for (double[] x : lonArcs(a)) {
      for (double[] y : lonArcs(b)) {
        w += Math.max(0, Math.min(x[1], y[1]) - Math.max(x[0], y[0]));
      }
    }
    return w;
  }

  /** Number of disjoint pieces of the intersection on the longitude circle (a touch is not one). */
  private static int lonPieces(Rectangle a, Rectangle b) {
    List<double[]> pieces = new ArrayList<>();
    for (double[] x : lonArcs(a)) {
      for (double[] y : lonArcs(b)) {
        double lo = Math.max(x[0], y[0]);
        double hi = Math.min(x[1], y[1]);
        if (hi > lo) {
          pieces.add(new double[] {lo, hi});
        }
      }
    }
    int n = pieces.size();
    // pieces that end at +180 and start at -180 are one arc on the circle
    boolean at180 = false, atMinus180 = false;
    for (double[] p : pieces) {
      if (p[1] == 180) at180 = true;
      if (p[0] == -180) atMinus180 = true;
    }
    if (at180 && atMinus180) n--;
    return n;
  }

  /** Javadoc formula with minSideLength = 0, for boxes with positive width and height. */
  private static double expectedScore(Rectangle query, Rectangle target, double p) {
    double height =
        Math.min(query.getMaxY(), target.getMaxY()) - Math.max(query.getMinY(), target.getMinY());
    if (height < 0) return 0;
    double inter = lonIntersection(query, target) * height;
    double qArea = query.getWidth() * query.getHeight();
    double tArea = target.getWidth() * target.getHeight();
    return inter / qArea * p + inter / tArea * (1 - p);
  }

  /** Indexes the given boxes (one doc each, stored "id" = index) and returns an open reader. */
  private DirectoryReader index(Directory dir, BBoxStrategy strategy, List<Rectangle> boxes)
      throws Exception {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < boxes.size(); i++) {
        Document doc = new Document();
        doc.add(new StoredField("id", i));
        for (Field f : strategy.createIndexableFields(boxes.get(i))) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
    }
    return DirectoryReader.open(dir);
  }

  /** Score of the single indexed doc through the public DoubleValues API. */
  private static double valueOf(DoubleValuesSource vs, DirectoryReader reader) throws Exception {
    assertEquals(1, reader.maxDoc());
    LeafReaderContext leaf = reader.leaves().get(0);
    DoubleValues dv = vs.getValues(leaf, null);
    assertTrue(dv.advanceExact(0));
    return dv.doubleValue();
  }

  /**
   * Query covers [60,180] U [-180,-60] (240 deg wide), target covers [140,180] U [-180,100] (320 deg
   * wide), both 60 deg tall. Their longitude intersection is [140,180] U [-180,-60] (160 deg, the
   * arc through the dateline) plus [60,100] (40 deg): 200 deg in two disjoint pieces.
   */
  public void testBothCrossTwoPieceIntersection() throws Exception {
    BBoxStrategy strategy = BBoxStrategy.newInstance(ctx, "bbox");
    Rectangle query = rect(60, -60, -30, 30);
    Rectangle target = rect(140, 100, -30, 30);
    assertTrue(query.getCrossesDateLine());
    assertTrue(target.getCrossesDateLine());
    assertEquals(240.0, query.getWidth(), 0);
    assertEquals(320.0, target.getWidth(), 0);
    assertEquals(200.0, lonIntersection(query, target), 0);
    assertEquals(2, lonPieces(query, target));

    // independent arithmetic: I = 200*60 = 12000, Aq = 240*60 = 14400, At = 320*60 = 19200
    double expected = 0.5 * 12000.0 / 14400.0 + 0.5 * 12000.0 / 19200.0; // 0.7291666...
    assertEquals(expected, expectedScore(query, target, 0.5), EPS);

    try (Directory dir = newDirectory();
        DirectoryReader reader = index(dir, strategy, List.of(target))) {
      DoubleValuesSource vs = strategy.makeOverlapRatioValueSource(query, 0.5);
      double actual = valueOf(vs, reader);
      Explanation e =
          vs.explain(reader.leaves().get(0), 0, Explanation.match(0f, "unused score"));
      // the explanation is printed so the reported intersection width is visible in the log
      System.out.println("VERIFY-V1 query=" + query + " target=" + target);
      System.out.println("VERIFY-V1 expected=" + expected + " actual=" + actual);
      System.out.println("VERIFY-V1 explain:\n" + e);
      assertEquals(
          "score must use the full intersection (both longitude pieces, width 200)",
          expected,
          actual,
          EPS);
    }
  }

  /** Same pair with query and target swapped: the formula is symmetric in the intersection. */
  public void testBothCrossTwoPieceIntersectionSwapped() throws Exception {
    BBoxStrategy strategy = BBoxStrategy.newInstance(ctx, "bbox");
    Rectangle query = rect(140, 100, -30, 30);
    Rectangle target = rect(60, -60, -30, 30);
    double expected = 0.5 * 12000.0 / 19200.0 + 0.5 * 12000.0 / 14400.0;
    try (Directory dir = newDirectory();
        DirectoryReader reader = index(dir, strategy, List.of(target))) {
      double actual = valueOf(strategy.makeOverlapRatioValueSource(query, 0.5), reader);
      System.out.println("VERIFY-V1 swapped expected=" + expected + " actual=" + actual);
      assertEquals(expected, actual, EPS);
    }
  }

  /**
   * Control (expected to PASS): the same query against a NON-crossing target whose intersection is
   * also two disjoint pieces. The one-crossing branch sums both pieces, so the class does intend a
   * two-piece intersection to count fully.
   */
  public void testControlOneCrossTwoPieceIsSummed() throws Exception {
    BBoxStrategy strategy = BBoxStrategy.newInstance(ctx, "bbox");
    Rectangle query = rect(60, -60, -30, 30); // crosses
    Rectangle target = rect(-170, 170, -30, 30); // does not cross; 340 wide
    assertFalse(target.getCrossesDateLine());
    assertEquals(220.0, lonIntersection(query, target), 0); // [60,170] U [-170,-60]
    assertEquals(2, lonPieces(query, target));
    double expected = expectedScore(query, target, 0.5);
    try (Directory dir = newDirectory();
        DirectoryReader reader = index(dir, strategy, List.of(target))) {
      double actual = valueOf(strategy.makeOverlapRatioValueSource(query, 0.5), reader);
      System.out.println("VERIFY-V1 control expected=" + expected + " actual=" + actual);
      assertEquals(expected, actual, EPS);
    }
  }

  /**
   * Ranking consequence through a real sorted search: doc A (the two-piece target) truly overlaps
   * the query more than doc B (a plain box), but is ranked below it.
   */
  public void testRankingInversion() throws Exception {
    BBoxStrategy strategy = BBoxStrategy.newInstance(ctx, "bbox");
    Rectangle query = rect(60, -60, -30, 30);
    Rectangle docA = rect(140, 100, -30, 30); // true score 0.7291666
    Rectangle docB = rect(40, 160, -30, 30); // true score 0.625 (I = 100*60)
    double trueA = expectedScore(query, docA, 0.5);
    double trueB = expectedScore(query, docB, 0.5);
    assertEquals(0.625, trueB, EPS);
    assertTrue(trueA > trueB);
    try (Directory dir = newDirectory();
        DirectoryReader reader = index(dir, strategy, List.of(docA, docB))) {
      IndexSearcher searcher = newSearcher(reader);
      DoubleValuesSource vs = strategy.makeOverlapRatioValueSource(query, 0.5);
      TopFieldDocs top = searcher.search(new MatchAllDocsQuery(), 2, new Sort(vs.getSortField(true)));
      int firstId =
          searcher.storedFields().document(top.scoreDocs[0].doc).getField("id").numericValue().intValue();
      System.out.println(
          "VERIFY-V1 ranking trueA=" + trueA + " trueB=" + trueB + " top-ranked id=" + firstId
              + " (0=A, 1=B)");
      assertEquals("doc A has the larger true overlap and must rank first", 0, firstId);
    }
  }

  /**
   * Sweep over all geo boxes with longitudes on a 30-degree grid (fixed latitude band), every
   * ordered (query, target) pair, p = 0.5. Classifies every mismatch against the javadoc formula.
   * Asserts (a) zero mismatches outside the class "both cross AND two-piece intersection" (oracle
   * sanity), then (b) zero mismatches overall (fails if the bug is present).
   */
  public void testGridSweepClassification() throws Exception {
    BBoxStrategy strategy = BBoxStrategy.newInstance(ctx, "bbox");
    List<Rectangle> boxes = new ArrayList<>();
    java.util.Set<String> seen = new java.util.HashSet<>();
    for (int minX = -180; minX <= 180; minX += 30) {
      for (int maxX = -180; maxX <= 180; maxX += 30) {
        if (minX == maxX) continue;
        Rectangle r = rect(minX, maxX, -30, 30);
        if (r.getWidth() == 0) continue; // e.g. rect(180,-180) normalizes to a zero-width line
        if (seen.add(r.getMinX() + "," + r.getMaxX())) {
          boxes.add(r);
        }
      }
    }
    long checks = 0, bothCrossTwoPiece = 0, mismatchBothCrossTwoPiece = 0, mismatchOther = 0;
    String firstOther = null, firstBug = null;
    try (Directory dir = newDirectory();
        DirectoryReader reader = index(dir, strategy, boxes)) {
      for (Rectangle query : boxes) {
        DoubleValuesSource vs = strategy.makeOverlapRatioValueSource(query, 0.5);
        for (LeafReaderContext leaf : reader.leaves()) {
          DoubleValues dv = vs.getValues(leaf, null);
          ShapeValues sv = strategy.makeShapeValueSource().getValues(leaf);
          for (int doc = 0; doc < leaf.reader().maxDoc(); doc++) {
            assertTrue(sv.advanceExact(doc));
            Rectangle t = (Rectangle) sv.value();
            Rectangle target = rect(t.getMinX(), t.getMaxX(), t.getMinY(), t.getMaxY());
            assertTrue(dv.advanceExact(doc));
            double actual = dv.doubleValue();
            double expected = expectedScore(query, target, 0.5);
            boolean cls =
                query.getCrossesDateLine()
                    && target.getCrossesDateLine()
                    && lonPieces(query, target) == 2;
            checks++;
            if (cls) bothCrossTwoPiece++;
            if (Math.abs(actual - expected) > EPS) {
              String msg = "q=" + query + " t=" + target + " exp=" + expected + " act=" + actual;
              if (cls) {
                mismatchBothCrossTwoPiece++;
                if (firstBug == null) firstBug = msg;
              } else {
                mismatchOther++;
                if (firstOther == null) firstOther = msg;
              }
            }
          }
        }
      }
    }
    System.out.println(
        "VERIFY-V1 sweep boxes="
            + boxes.size()
            + " checks="
            + checks
            + " bothCrossTwoPiecePairs="
            + bothCrossTwoPiece
            + " mismatchBothCrossTwoPiece="
            + mismatchBothCrossTwoPiece
            + " mismatchOther="
            + mismatchOther);
    System.out.println("VERIFY-V1 sweep first bug-class mismatch: " + firstBug);
    System.out.println("VERIFY-V1 sweep first other mismatch: " + firstOther);
    assertEquals("mismatches outside the both-cross two-piece class: " + firstOther, 0, mismatchOther);
    assertEquals(
        "both-cross two-piece mismatches (first: " + firstBug + ")", 0, mismatchBothCrossTwoPiece);
  }
}
