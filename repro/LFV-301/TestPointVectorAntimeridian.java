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
import org.apache.lucene.search.Query;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.bbox.BBoxStrategy;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.Shape;

/**
 * Independent check: in a geographic SpatialContext the meridians x=-180 and x=+180 are the same
 * line. A point stored at one sign must be found by a non-dateline-crossing rectangle whose edge
 * is written with the other sign, as SpatialOperation#evaluate (the module's predicate definition)
 * says. Uses public API only.
 */
public class TestPointVectorAntimeridian extends LuceneTestCase {

  private static final SpatialContext CTX = SpatialContext.GEO;

  /** Index one point with the given strategy and count hits of (op, query). */
  private static int count(SpatialStrategy strategy, Shape indexed, SpatialOperation op, Shape query)
      throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      Document doc = new Document();
      for (Field f : strategy.createIndexableFields(indexed)) {
        doc.add(f);
      }
      w.addDocument(doc);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        Query q = strategy.makeQuery(new SpatialArgs(op, query));
        return s.count(q);
      }
    }
  }

  private static PointVectorStrategy pv() {
    return PointVectorStrategy.newInstance(CTX, "loc");
  }

  /** Point stored at +180, query rectangle starts at -180 (does not cross the dateline). */
  public void testPlus180PointWestEdgeMinus180Intersects() throws IOException {
    Point p = CTX.getShapeFactory().pointXY(180, 0);
    Rectangle q = CTX.getShapeFactory().rect(-180, -170, -10, 10);
    assertEquals(180.0, p.getX(), 0.0); // not normalized by the context
    assertEquals(-180.0, q.getMinX(), 0.0);
    assertFalse(q.getCrossesDateLine());
    assertTrue("oracle: SpatialOperation.Intersects", SpatialOperation.Intersects.evaluate(p, q));
    assertEquals(
        "PointVectorStrategy Intersects " + q + " must find " + p,
        1,
        count(pv(), p, SpatialOperation.Intersects, q));
  }

  /** Point stored at -180, query rectangle ends at +180 (does not cross the dateline). */
  public void testMinus180PointEastEdgePlus180IsWithin() throws IOException {
    Point p = CTX.getShapeFactory().pointXY(-180, 0);
    Rectangle q = CTX.getShapeFactory().rect(170, 180, -10, 10);
    assertEquals(-180.0, p.getX(), 0.0);
    assertEquals(180.0, q.getMaxX(), 0.0);
    assertFalse(q.getCrossesDateLine());
    assertTrue("oracle: SpatialOperation.IsWithin", SpatialOperation.IsWithin.evaluate(p, q));
    assertEquals(
        "PointVectorStrategy IsWithin " + q + " must find " + p,
        1,
        count(pv(), p, SpatialOperation.IsWithin, q));
  }

  /** Control: same-sign edges work, so the setup itself is sound. Expected to pass. */
  public void testControlSameSignEdges() throws IOException {
    Point pm = CTX.getShapeFactory().pointXY(-180, 0);
    Point pp = CTX.getShapeFactory().pointXY(180, 0);
    Rectangle west = CTX.getShapeFactory().rect(-180, -170, -10, 10);
    Rectangle east = CTX.getShapeFactory().rect(170, 180, -10, 10);
    assertEquals(1, count(pv(), pm, SpatialOperation.Intersects, west));
    assertEquals(1, count(pv(), pp, SpatialOperation.Intersects, east));
    assertEquals(1, count(pv(), pm, SpatialOperation.IsWithin, west));
    assertEquals(1, count(pv(), pp, SpatialOperation.IsWithin, east));
  }

  /**
   * Control: BBoxStrategy (same module) answers the opposite-sign cases as the oracle does.
   * Expected to pass.
   */
  public void testControlBBoxStrategyOppositeSign() throws IOException {
    BBoxStrategy bbox = BBoxStrategy.newInstance(CTX, "bbox");
    Point pp = CTX.getShapeFactory().pointXY(180, 0);
    Point pm = CTX.getShapeFactory().pointXY(-180, 0);
    Rectangle west = CTX.getShapeFactory().rect(-180, -170, -10, 10);
    Rectangle east = CTX.getShapeFactory().rect(170, 180, -10, 10);
    assertEquals(1, count(bbox, pp, SpatialOperation.Intersects, west));
    assertEquals(1, count(bbox, pm, SpatialOperation.IsWithin, east));
  }
}
