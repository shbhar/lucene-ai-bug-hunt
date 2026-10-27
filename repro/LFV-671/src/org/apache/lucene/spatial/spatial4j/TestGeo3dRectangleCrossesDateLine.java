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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.bbox.BBoxStrategy;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.vector.PointVectorStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.Shape;

/**
 * Geo3dRectangleShape.getCrossesDateLine() tests "minX < 0 < maxX" (a box that spans the prime
 * meridian) instead of "minX > maxX" (a box that spans the dateline), so strategies that branch on
 * it treat dateline boxes as empty longitude ranges and prime-meridian boxes as two half-worlds.
 */
public class TestGeo3dRectangleCrossesDateLine extends LuceneTestCase {

  private static final SpatialContext GEO3D = new Geo3dSpatialContextFactory().newSpatialContext();

  /** The Rectangle contract, as implemented by spatial4j's own RectangleImpl: minX > maxX. */
  public void testFlagMatchesRectangleContract() {
    Rectangle dateline = GEO3D.getShapeFactory().rect(170, -170, -10, 10);
    Rectangle primeMeridian = GEO3D.getShapeFactory().rect(-10, 10, -10, 10);
    assertEquals(
        "spatial4j reference",
        true,
        SpatialContext.GEO.getShapeFactory().rect(170, -170, -10, 10).getCrossesDateLine());
    assertTrue("box 170..-170 crosses the dateline", dateline.getCrossesDateLine());
    assertFalse("box -10..10 does not cross the dateline", primeMeridian.getCrossesDateLine());
  }

  private static int count(SpatialStrategy strategy, Shape indexed, Shape query) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(indexed)) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        return s.count(strategy.makeQuery(new SpatialArgs(SpatialOperation.Intersects, query)));
      }
    }
  }

  /** A point at longitude 175 is inside the box 170..-170 (it spans the dateline). */
  public void testPointVectorStrategyDatelineBox() throws Exception {
    PointVectorStrategy strategy = PointVectorStrategy.newInstance(GEO3D, "pv");
    Shape point = GEO3D.getShapeFactory().pointXY(175, 0);
    Rectangle query = GEO3D.getShapeFactory().rect(170, -170, -10, 10);
    assertEquals("Geo3d relation of the query and the point", true, query.relate(point).intersects());
    assertEquals("PointVectorStrategy Intersects hits", 1, count(strategy, point, query));
  }

  /** A circle around (180, 0): its bounding box spans the dateline. */
  public void testPointVectorStrategyDatelineCircle() throws Exception {
    PointVectorStrategy strategy = PointVectorStrategy.newInstance(GEO3D, "pv");
    Shape point = GEO3D.getShapeFactory().pointXY(-178, 1);
    Shape query = GEO3D.getShapeFactory().circle(180, 0, 5);
    assertEquals("Geo3d relation of the query and the point", true, query.relate(point).intersects());
    assertEquals("PointVectorStrategy Intersects hits", 1, count(strategy, point, query));
  }

  /** BBoxStrategy indexes the flag: a box spanning the dateline is missed by a box inside it. */
  public void testBBoxStrategyDatelineBoxMissed() throws Exception {
    BBoxStrategy strategy = BBoxStrategy.newInstance(GEO3D, "bbox");
    Rectangle datelineDoc = GEO3D.getShapeFactory().rect(170, -170, -10, 10);
    Rectangle insideIt = GEO3D.getShapeFactory().rect(175, 176, -1, 1);
    assertEquals("Geo3d relation", true, datelineDoc.relate(insideIt).intersects());
    assertEquals("175..176 lies inside 170..-170", 1, count(strategy, datelineDoc, insideIt));
  }

  /** ... and a box spanning the prime meridian matches a box on the other side of the planet. */
  public void testBBoxStrategyPrimeMeridianBoxFalseMatch() throws Exception {
    BBoxStrategy strategy = BBoxStrategy.newInstance(GEO3D, "bbox");
    Rectangle primeMeridianDoc = GEO3D.getShapeFactory().rect(-10, 10, -10, 10);
    Rectangle farAway = GEO3D.getShapeFactory().rect(100, 110, -5, 5);
    assertEquals("Geo3d relation", false, primeMeridianDoc.relate(farAway).intersects());
    assertEquals("100..110 is far from -10..10", 0, count(strategy, primeMeridianDoc, farAway));
  }
}
