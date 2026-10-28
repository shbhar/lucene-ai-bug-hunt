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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.spatial.ShapeValues;
import org.apache.lucene.spatial.ShapeValuesSource;
import org.apache.lucene.spatial.spatial4j.Geo3dSpatialContextFactory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;

/**
 * Independent check: Rectangle.reset on the Geo3d spatial context, directly and through
 * BBoxStrategy's per-document value sources. Every "geo3d" test has a "control" twin that runs
 * the identical code with spatial4j's own SpatialContext.GEO; the controls are expected to pass.
 * Public API only.
 */
public class TestGeo3dRectResetVerify extends LuceneTestCase {

  private static SpatialContext geo3d() {
    return new Geo3dSpatialContextFactory().newSpatialContext();
  }

  private static String label(SpatialContext ctx) {
    return ctx == SpatialContext.GEO ? "spatial4j GEO" : "Geo3d";
  }

  // --- 1. Rectangle.reset contract, no index ---

  private static void checkDirectReset(SpatialContext ctx) {
    // start from a non-zero rectangle so "old value" is distinguishable from "zero"
    Rectangle r = ctx.getShapeFactory().rect(-50, -40, -30, -20);
    r.reset(10, 20, 30, 40);
    Rectangle fresh = ctx.getShapeFactory().rect(10, 20, 30, 40);
    String msg = label(ctx) + "/" + r.getClass().getSimpleName() + " ";
    assertEquals(msg + "minX", fresh.getMinX(), r.getMinX(), 0.0);
    assertEquals(msg + "maxX", fresh.getMaxX(), r.getMaxX(), 0.0);
    assertEquals(msg + "minY", fresh.getMinY(), r.getMinY(), 0.0);
    assertEquals(msg + "maxY", fresh.getMaxY(), r.getMaxY(), 0.0);
    assertEquals(msg + "width", fresh.getWidth(), r.getWidth(), 0.0);
    assertEquals(msg + "height", fresh.getHeight(), r.getHeight(), 0.0);
  }

  public void testDirectResetGeo3d() {
    checkDirectReset(geo3d());
  }

  public void testDirectResetControlSpatial4jGeo() {
    checkDirectReset(SpatialContext.GEO);
  }

  /** Observation, not an assertion of the bug: equals() sees the new box after reset. */
  public void testGeo3dEqualsAfterResetIsConsistentWithFresh() {
    SpatialContext ctx = geo3d();
    Rectangle r = ctx.getShapeFactory().rect(-50, -40, -30, -20);
    r.reset(10, 20, 30, 40);
    assertEquals(ctx.getShapeFactory().rect(10, 20, 30, 40), r);
  }

  // --- 2. BBoxStrategy value sources ---

  private static final double[][] BOXES = {
    {10, 20, 30, 40}, // minX, maxX, minY, maxY
    {-100, -90, -60, -50}
  };

  private interface LeafCheck {
    void check(LeafReaderContext leaf, int doc, double[] box) throws Exception;
  }

  private void forEachIndexedDoc(SpatialContext ctx, BBoxStrategy strategy, LeafCheck check)
      throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        for (int i = 0; i < BOXES.length; i++) {
          double[] b = BOXES[i];
          Document doc = new Document();
          doc.add(new NumericDocValuesField("id", i));
          for (Field f : strategy.createIndexableFields(ctx.getShapeFactory().rect(b[0], b[1], b[2], b[3]))) {
            doc.add(f);
          }
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        int seen = 0;
        for (LeafReaderContext leaf : reader.leaves()) {
          NumericDocValues ids = DocValues.getNumeric(leaf.reader(), "id");
          for (int doc = 0; doc < leaf.reader().maxDoc(); doc++) {
            assertTrue(ids.advanceExact(doc));
            check.check(leaf, doc, BOXES[(int) ids.longValue()]);
            seen++;
          }
        }
        assertEquals(BOXES.length, seen);
      }
    }
  }

  private void checkShapeValues(SpatialContext ctx) throws Exception {
    BBoxStrategy strategy = BBoxStrategy.newInstance(ctx, "bbox");
    ShapeValuesSource svs = strategy.makeShapeValueSource();
    forEachIndexedDoc(
        ctx,
        strategy,
        (leaf, doc, b) -> {
          ShapeValues sv = svs.getValues(leaf);
          assertTrue(sv.advanceExact(doc));
          Rectangle got = (Rectangle) sv.value();
          String msg = label(ctx) + " doc box " + java.util.Arrays.toString(b) + " ";
          assertEquals(msg + "minX", b[0], got.getMinX(), 0.0);
          assertEquals(msg + "maxX", b[1], got.getMaxX(), 0.0);
          assertEquals(msg + "minY", b[2], got.getMinY(), 0.0);
          assertEquals(msg + "maxY", b[3], got.getMaxY(), 0.0);
        });
  }

  public void testShapeValuesGeo3d() throws Exception {
    checkShapeValues(geo3d());
  }

  public void testShapeValuesControlSpatial4jGeo() throws Exception {
    checkShapeValues(SpatialContext.GEO);
  }

  /** Overlap-ratio score of each indexed box against a query equal to that box: 1.0. */
  private void checkOverlapRatioSelf(SpatialContext ctx) throws Exception {
    BBoxStrategy strategy = BBoxStrategy.newInstance(ctx, "bbox");
    forEachIndexedDoc(
        ctx,
        strategy,
        (leaf, doc, b) -> {
          Rectangle query = ctx.getShapeFactory().rect(b[0], b[1], b[2], b[3]);
          DoubleValuesSource dvs = strategy.makeOverlapRatioValueSource(query, 0.5);
          DoubleValues dv = dvs.getValues(leaf, null);
          assertTrue(dv.advanceExact(doc));
          assertEquals(
              label(ctx) + " self-overlap of " + java.util.Arrays.toString(b),
              1.0,
              dv.doubleValue(),
              1e-9);
        });
  }

  public void testOverlapRatioSelfGeo3d() throws Exception {
    checkOverlapRatioSelf(geo3d());
  }

  public void testOverlapRatioSelfControlSpatial4jGeo() throws Exception {
    checkOverlapRatioSelf(SpatialContext.GEO);
  }
}
