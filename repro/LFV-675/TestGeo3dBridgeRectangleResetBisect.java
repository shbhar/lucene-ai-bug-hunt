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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.spatial.ShapeValues;
import org.apache.lucene.spatial.bbox.BBoxStrategy;
import org.apache.lucene.spatial3d.geom.GeoBBoxFactory;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.Shape;

/**
 * Version-portable boundary test: the same source compiles against Lucene 7.0.x (before
 * Geo3dRectangleShape and the Geo3d spatial context existed) and every later release. It uses
 * only the public Geo3dShape wrapper under the default geo SpatialContext.
 *
 * <p>testBoundingBoxRectangleReset: the bounding box a Geo3dShape returns is a spatial4j
 * Rectangle; Rectangle.reset must replace its coordinates. The two other tests are controls.
 */
public class TestGeo3dBridgeRectangleResetBisect extends LuceneTestCase {

  private static final SpatialContext CTX = SpatialContext.GEO;
  private static final double RAD = Math.PI / 180;

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Shape geo3dBox(double minX, double maxX, double minY, double maxY) {
    return new Geo3dShape(
        GeoBBoxFactory.makeGeoBBox(
            PlanetModel.SPHERE, maxY * RAD, minY * RAD, minX * RAD, maxX * RAD),
        CTX);
  }

  private static void assertBounds(String what, Rectangle r, double tol, double... expected) {
    assertEquals(what + " minX", expected[0], r.getMinX(), tol);
    assertEquals(what + " maxX", expected[1], r.getMaxX(), tol);
    assertEquals(what + " minY", expected[2], r.getMinY(), tol);
    assertEquals(what + " maxY", expected[3], r.getMaxY(), tol);
  }

  /** The Rectangle handed out by the bridge must follow Rectangle.reset. */
  public void testBoundingBoxRectangleReset() {
    Rectangle bbox = geo3dBox(-50, -40, -30, -20).getBoundingBox();
    String type = bbox.getClass().getSimpleName();
    bbox.reset(10, 20, 30, 40);
    assertBounds(type + " after reset(10, 20, 30, 40)", bbox, 0, 10, 20, 30, 40);
  }

  /** Control: a spatial4j rectangle of the default geo context. */
  public void testSpatial4jRectangleResetControl() {
    Rectangle r = CTX.makeRectangle(-50, -40, -30, -20);
    r.reset(10, 20, 30, 40);
    assertBounds("spatial4j rectangle after reset(10, 20, 30, 40)", r, 0, 10, 20, 30, 40);
  }

  private static String fmt(Rectangle r) {
    return String.format(
        Locale.ROOT, "[%.0f, %.0f, %.0f, %.0f]", r.getMinX(), r.getMaxX(), r.getMinY(), r.getMaxY());
  }

  /**
   * Control for the usage pattern that already worked before 7.1: BBoxStrategy in the default geo
   * context indexing Geo3dShape boxes. Its shape values reset a rectangle of that context, so they
   * are expected to be right on every version.
   */
  public void testBBoxStrategyShapeValuesDefaultContextControl() throws Exception {
    BBoxStrategy strategy = BBoxStrategy.newInstance(CTX, "bbox");
    List<String> expected = new ArrayList<>();
    expected.add("[10, 20, 30, 40]");
    expected.add("[50, 60, -20, -10]");
    List<String> actual = new ArrayList<>();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        for (Shape s : new Shape[] {geo3dBox(10, 20, 30, 40), geo3dBox(50, 60, -20, -10)}) {
          Document doc = new Document();
          for (Field f : strategy.createIndexableFields(s)) {
            doc.add(f);
          }
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        for (LeafReaderContext leaf : reader.leaves()) {
          ShapeValues values = strategy.makeShapeValueSource().getValues(leaf);
          for (int doc = 0; doc < leaf.reader().maxDoc(); doc++) {
            assertTrue(values.advanceExact(doc));
            // rounded to whole degrees: the pre-7.1 bounding box is buffered by 0.05 degrees
            actual.add(fmt((Rectangle) values.value()));
          }
        }
      }
    }
    Collections.sort(expected);
    Collections.sort(actual);
    assertEquals(expected, actual);
  }
}
