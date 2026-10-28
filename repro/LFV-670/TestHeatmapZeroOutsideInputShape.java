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
package org.apache.lucene.spatial.prefix;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.spatial.prefix.tree.GeohashPrefixTree;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.spatial.prefix.tree.SpatialPrefixTree;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.context.SpatialContextFactory;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.ShapeCollection;
import org.locationtech.spatial4j.shape.SpatialRelation;
import org.locationtech.spatial4j.shape.impl.RectangleImpl;

/**
 * Independent check of the HeatmapFacetCounter.calcFacets contract for a non-rectangular input
 * shape: "We always return a rectangle of integers even if the inputShape isn't a rectangle -- the
 * non-intersecting cells will all be 0."
 *
 * <p>Uses only public API: RecursivePrefixTreeStrategy (default settings), createIndexableFields,
 * PrefixTreeStrategy.calcFacets.
 */
public class TestHeatmapZeroOutsideInputShape extends LuceneTestCase {

  private static SpatialContext nonGeo(double half) {
    SpatialContextFactory f = new SpatialContextFactory();
    f.geo = false;
    f.worldBounds = new RectangleImpl(-half, half, -half, half, null);
    return f.newSpatialContext();
  }

  /** Indexes one document per shape and returns the heatmap for the input shape. */
  private static HeatmapFacetCounter.Heatmap heatmap(
      SpatialPrefixTree grid, List<Shape> docs, Shape input, int facetLevel) throws IOException {
    RecursivePrefixTreeStrategy strategy = new RecursivePrefixTreeStrategy(grid, "geo");
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (Shape s : docs) {
          Document d = new Document();
          for (Field f : strategy.createIndexableFields(s)) {
            d.add(f);
          }
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        return strategy.calcFacets(r.getContext(), null, input, facetLevel, 100_000);
      }
    }
  }

  private static Rectangle cellRect(SpatialContext ctx, HeatmapFacetCounter.Heatmap hm, int c, int r) {
    double w = hm.region.getWidth() / hm.columns;
    double h = hm.region.getHeight() / hm.rows;
    double minX = hm.region.getMinX() + c * w;
    double minY = hm.region.getMinY() + r * h;
    return ctx.getShapeFactory().rect(minX, minX + w, minY, minY + h);
  }

  /**
   * Asserts the documented contract: every heatmap cell that is DISJOINT from the input shape has
   * count 0. Also asserts the check is not vacuous (some cell is disjoint, some count is non-zero).
   */
  private static void assertDisjointCellsZero(
      SpatialContext ctx, HeatmapFacetCounter.Heatmap hm, Shape input) {
    assertFalse("heatmap region must not cross the dateline here", hm.region.getCrossesDateLine());
    int disjoint = 0;
    int nonZero = 0;
    StringBuilder bad = new StringBuilder();
    int badCount = 0;
    for (int c = 0; c < hm.columns; c++) {
      for (int r = 0; r < hm.rows; r++) {
        int count = hm.getCount(c, r);
        if (count != 0) nonZero++;
        Rectangle cell = cellRect(ctx, hm, c, r);
        if (input.relate(cell) == SpatialRelation.DISJOINT) {
          disjoint++;
          if (count != 0) {
            badCount++;
            if (badCount <= 6) bad.append("\n  ").append(cell).append(" count=").append(count);
          }
        }
      }
    }
    assertTrue("vacuous: no heatmap cell is disjoint from the input shape", disjoint > 0);
    assertTrue("vacuous: heatmap is all zeros", nonZero > 0);
    assertEquals(
        hm
            + ": "
            + badCount
            + " of "
            + disjoint
            + " cells disjoint from the input shape have a non-zero count; first ones:"
            + bad,
        0,
        badCount);
  }

  /** Non-geo world [-128,128]^2, quad tree; doc = the grid-aligned square [0,64]^2; input circle. */
  public void testCircleInputCoarseLeafDoc() throws IOException {
    SpatialContext ctx = nonGeo(128);
    SpatialPrefixTree grid = new QuadPrefixTree(ctx, 6);
    Shape doc = ctx.getShapeFactory().rect(0, 64, 0, 64);
    Shape input = ctx.getShapeFactory().circle(0, 0, 60);
    HeatmapFacetCounter.Heatmap hm = heatmap(grid, List.of(doc), input, 5);
    assertDisjointCellsZero(ctx, hm, input);
  }

  /**
   * The indexed leaf cell contains the whole heatmap region (the "all cells" ancestor path): input
   * circle(64,64,20) lies inside the doc [0,128]^2, a level-1 quad cell.
   */
  public void testCircleInputLeafCoversWholeHeatmap() throws IOException {
    SpatialContext ctx = nonGeo(128);
    SpatialPrefixTree grid = new QuadPrefixTree(ctx, 6);
    Shape doc = ctx.getShapeFactory().rect(0, 128, 0, 128);
    Shape input = ctx.getShapeFactory().circle(64, 64, 20);
    HeatmapFacetCounter.Heatmap hm = heatmap(grid, List.of(doc), input, 4);
    assertDisjointCellsZero(ctx, hm, input);
  }

  /** Same world; input = a collection of two small rectangles in opposite corners. */
  public void testShapeCollectionInputCoarseLeafDoc() throws IOException {
    SpatialContext ctx = nonGeo(128);
    SpatialPrefixTree grid = new QuadPrefixTree(ctx, 6);
    Shape doc = ctx.getShapeFactory().rect(0, 128, 0, 128); // one quarter of the world
    Shape input =
        new ShapeCollection<>(
            Arrays.asList(
                ctx.getShapeFactory().rect(-120, -100, -120, -100),
                ctx.getShapeFactory().rect(100, 120, 100, 120)),
            ctx);
    HeatmapFacetCounter.Heatmap hm = heatmap(grid, List.of(doc), input, 3);
    assertDisjointCellsZero(ctx, hm, input);
  }

  /** Geo context, geohash tree (a common heatmap configuration); input = geodesic circle. */
  public void testGeoCircleInputGeohash() throws IOException {
    SpatialContext ctx = SpatialContext.GEO;
    SpatialPrefixTree grid = new GeohashPrefixTree(ctx, 4);
    // exactly one level-1 geohash cell (45 x 45 degrees): indexed as a leaf coarser than facetLevel
    Shape doc = ctx.getShapeFactory().rect(0, 45, 0, 45);
    Shape input = ctx.getShapeFactory().circle(0, 0, 18);
    HeatmapFacetCounter.Heatmap hm = heatmap(grid, List.of(doc), input, 2);
    assertDisjointCellsZero(ctx, hm, input);
  }

  /** Control: the same circle with point documents only; disjoint cells are 0 (passes). */
  public void testControlPointsOnly() throws IOException {
    SpatialContext ctx = nonGeo(128);
    SpatialPrefixTree grid = new QuadPrefixTree(ctx, 6);
    List<Shape> docs =
        List.of(
            ctx.getShapeFactory().pointXY(61, 61), // inside the circle's bbox, outside the circle
            ctx.getShapeFactory().pointXY(5, 5)); // inside the circle
    Shape input = ctx.getShapeFactory().circle(0, 0, 60);
    HeatmapFacetCounter.Heatmap hm = heatmap(grid, docs, input, 5);
    assertDisjointCellsZero(ctx, hm, input);
  }

  /**
   * Control: with the circle's bounding box (a rectangle) as input, the square document is counted
   * in exactly the heatmap cells it intersects (closed shapes, so cells touching its edges count
   * too) (passes). Shows where the counts in the circle case come from.
   */
  public void testControlRectangleInputCountsSquareCells() throws IOException {
    SpatialContext ctx = nonGeo(128);
    SpatialPrefixTree grid = new QuadPrefixTree(ctx, 6);
    Shape doc = ctx.getShapeFactory().rect(0, 64, 0, 64);
    Shape input = ctx.getShapeFactory().circle(0, 0, 60).getBoundingBox();
    HeatmapFacetCounter.Heatmap hm = heatmap(grid, List.of(doc), input, 5);
    int ones = 0;
    for (int c = 0; c < hm.columns; c++) {
      for (int r = 0; r < hm.rows; r++) {
        Rectangle cell = cellRect(ctx, hm, c, r);
        int expected = doc.relate(cell) == SpatialRelation.DISJOINT ? 0 : 1;
        assertEquals(cell.toString(), expected, hm.getCount(c, r));
        ones += expected;
      }
    }
    assertEquals(8 * 8 + 8 + 8 + 1, ones); // interior 8x8 + the cells touching x=0, y=0, (0,0)
  }
}
