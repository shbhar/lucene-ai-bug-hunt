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
package org.apache.lucene.spatial.vector;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.bbox.BBoxStrategy;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Rectangle;

/**
 * In a geo context, longitude -180 and +180 are the same meridian: {@link SpatialOperation}
 * (spatial4j relate) and {@link BBoxStrategy} treat them as one. A rectangle that does not cross
 * the dateline but has an edge on it must therefore find a point stored there with the other sign.
 */
public class TestPointVectorDatelineEdge extends LuceneTestCase {

  private static final SpatialContext CTX = SpatialContext.GEO;

  private static final SpatialOperation[] OPS = {
    SpatialOperation.Intersects, SpatialOperation.IsWithin
  };

  private static Point point(double x, double y) {
    return CTX.getShapeFactory().pointXY(x, y);
  }

  private static Rectangle rect(double minX, double maxX, double minY, double maxY) {
    return CTX.getShapeFactory().rect(minX, maxX, minY, maxY);
  }

  /** Indexes one document with the point and counts the hits of the query. */
  private static int count(
      SpatialStrategy strategy, Point point, SpatialOperation op, Rectangle query)
      throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field field : strategy.createIndexableFields(point)) {
          doc.add(field);
        }
        writer.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        return new IndexSearcher(reader).count(strategy.makeQuery(new SpatialArgs(op, query)));
      }
    }
  }

  /** The oracle, BBoxStrategy and PointVectorStrategy must agree on the hit count. */
  private static void check(Point point, Rectangle query, int expected) throws IOException {
    for (SpatialOperation op : OPS) {
      String what = op + " " + query + " with " + point;
      assertEquals("oracle: " + what, expected == 1, op.evaluate(point, query));
      assertEquals(
          "BBoxStrategy " + what,
          expected,
          count(BBoxStrategy.newInstance(CTX, "bbox"), point, op, query));
      assertEquals(
          "PointVectorStrategy " + what,
          expected,
          count(PointVectorStrategy.newInstance(CTX, "pv"), point, op, query));
    }
  }

  /** The western hemisphere, written with its west edge at -180, misses a point stored at +180. */
  public void testWesternHemisphereFindsPointAtPlus180() throws IOException {
    check(point(180, 10), rect(-180, 0, -90, 90), 1);
  }

  /** The eastern hemisphere, written with its east edge at +180, misses a point stored at -180. */
  public void testEasternHemisphereFindsPointAtMinus180() throws IOException {
    check(point(-180, 10), rect(0, 180, -90, 90), 1);
  }

  /** The caller uses +180 for both, but spatial4j itself turns the box's west edge into -180. */
  public void testBoxWrittenFromPlus180FindsPointAtPlus180() throws IOException {
    Rectangle query = rect(180, -170, -10, 10);
    assertEquals(-180.0, query.getMinX(), 0.0);
    assertFalse(query.getCrossesDateLine());
    check(point(180, 0), query, 1);
  }

  /** Controls: same-sign edges, a box that crosses the dateline, and a point just outside. */
  public void testControls() throws IOException {
    check(point(-180, 10), rect(-180, 0, -90, 90), 1);
    check(point(180, 10), rect(0, 180, -90, 90), 1);
    check(point(180, 0), rect(170, -170, -10, 10), 1);
    check(point(-180, 0), rect(170, -170, -10, 10), 1);
    check(point(179.9, 0), rect(-180, -170, -10, 10), 0);
  }
}
