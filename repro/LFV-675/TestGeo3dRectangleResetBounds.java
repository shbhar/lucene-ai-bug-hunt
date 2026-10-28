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
package org.apache.lucene.spatial.spatial4j;

import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.spatial.ShapeValues;
import org.apache.lucene.spatial.bbox.BBoxStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;

/**
 * {@code Rectangle.reset} must replace all four bounds of a Geo3d rectangle, as it does for
 * spatial4j's own rectangles. {@code BBoxStrategy}'s shape values and overlap-ratio scores reuse
 * one rectangle per segment and reset it for every document. Each test runs the same steps with
 * {@code SpatialContext.GEO} first (the control), then with the Geo3d context.
 */
public class TestGeo3dRectangleResetBounds extends LuceneTestCase {

  private static final SpatialContext GEO3D = new Geo3dSpatialContextFactory().newSpatialContext();
  private static final SpatialContext[] CONTEXTS = {SpatialContext.GEO, GEO3D};

  // minX, maxX, minY, maxY. No box spans longitude 0 or crosses the dateline.
  private static final double[] P = {10, 20, 30, 40};
  private static final double[] Q = {15, 25, 35, 45};
  private static final double[][] DOCS = {P, Q};

  private static String name(SpatialContext ctx) {
    return ctx == GEO3D ? "Geo3d context" : "spatial4j context";
  }

  private static Rectangle rect(SpatialContext ctx, double[] b) {
    return ctx.getShapeFactory().rect(b[0], b[1], b[2], b[3]);
  }

  private static double[] bounds(Rectangle r) {
    return new double[] {r.getMinX(), r.getMaxX(), r.getMinY(), r.getMaxY()};
  }

  public void testResetReplacesBounds() {
    for (SpatialContext ctx : CONTEXTS) {
      Rectangle r = ctx.getShapeFactory().rect(-50, -40, -30, -20);
      r.reset(10, 20, 30, 40);
      assertEquals(name(ctx) + ": reset rectangle equals rect(10, 20, 30, 40)", rect(ctx, P), r);
      double[] got = bounds(r);
      assertArrayEquals(name(ctx) + ": bounds after reset " + Arrays.toString(got), P, got, 0);
    }
  }

  public void testShapeValuesReportEachDocumentsBox() throws Exception {
    for (SpatialContext ctx : CONTEXTS) {
      double[][] got = indexAndRead(ctx);
      double[][] boxes = {Arrays.copyOf(got[0], 4), Arrays.copyOf(got[1], 4)};
      String msg = name(ctx) + ": shape values of docs P, Q " + Arrays.deepToString(boxes);
      for (int id = 0; id < DOCS.length; id++) {
        assertArrayEquals(msg, DOCS[id], boxes[id], 0);
      }
    }
  }

  /** Query box P: doc P is equal to it (score 1.0), doc Q covers a quarter of it (score 0.25). */
  public void testOverlapRatioScores() throws Exception {
    double[] expected = {1.0, 0.25};
    for (SpatialContext ctx : CONTEXTS) {
      double[][] got = indexAndRead(ctx);
      double[] scores = {got[0][4], got[1][4]};
      String msg = name(ctx) + ": overlap-ratio scores of docs P, Q " + Arrays.toString(scores);
      assertArrayEquals(msg, expected, scores, 1e-9);
    }
  }

  /** Indexes DOCS; returns, per document id, its shape value bounds and its score for query P. */
  private double[][] indexAndRead(SpatialContext ctx) throws Exception {
    BBoxStrategy strategy = BBoxStrategy.newInstance(ctx, "bbox");
    double[][] out = new double[DOCS.length][];
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        for (int id = 0; id < DOCS.length; id++) {
          Document doc = new Document();
          doc.add(new NumericDocValuesField("id", id));
          for (Field f : strategy.createIndexableFields(rect(ctx, DOCS[id]))) {
            doc.add(f);
          }
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        for (LeafReaderContext leaf : reader.leaves()) {
          NumericDocValues ids = DocValues.getNumeric(leaf.reader(), "id");
          ShapeValues shapes = strategy.makeShapeValueSource().getValues(leaf);
          DoubleValues scores =
              strategy.makeOverlapRatioValueSource(rect(ctx, P), 0.5).getValues(leaf, null);
          for (int doc = 0; doc < leaf.reader().maxDoc(); doc++) {
            assertTrue(ids.advanceExact(doc));
            assertTrue(shapes.advanceExact(doc));
            assertTrue(scores.advanceExact(doc));
            double[] b = bounds((Rectangle) shapes.value());
            out[(int) ids.longValue()] =
                new double[] {b[0], b[1], b[2], b[3], scores.doubleValue()};
          }
        }
      }
    }
    return out;
  }
}
