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
import org.apache.lucene.spatial.bbox.BBoxStrategy;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial3d.geom.GeoBBoxFactory;
import org.apache.lucene.spatial3d.geom.GeoCircleFactory;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.Shape;

/**
 * Version-portable bisection test: the same source compiles against Lucene 7.0.x (before the
 * Geo3d Spatial4j factory existed) and every later release. It wraps spatial3d shapes in the
 * public Geo3dShape class under the default geo SpatialContext and checks the dateline flag of
 * their bounding box, which BBoxStrategy indexes. Expected: a box from 170 to -170 crosses the
 * dateline, a box from -10 to 10 does not (the Rectangle contract: minX > maxX).
 */
public class TestGeo3dBoundingBoxDatelineFlagBisect extends LuceneTestCase {

  private static final SpatialContext CTX = SpatialContext.GEO;
  private static final double RAD = Math.PI / 180;

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Shape geo3dBox(double minX, double maxX, double minY, double maxY) {
    return new Geo3dShape(
        GeoBBoxFactory.makeGeoBBox(
            PlanetModel.SPHERE, maxY * RAD, minY * RAD, minX * RAD, maxX * RAD),
        CTX);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Shape geo3dCircle(double lon, double lat, double radiusDeg) {
    return new Geo3dShape(
        GeoCircleFactory.makeGeoCircle(PlanetModel.SPHERE, lat * RAD, lon * RAD, radiusDeg * RAD),
        CTX);
  }

  private static String describe(Rectangle r) {
    return r.getClass().getSimpleName()
        + "[minX="
        + r.getMinX()
        + ", maxX="
        + r.getMaxX()
        + ", crossesDateLine="
        + r.getCrossesDateLine()
        + "]";
  }

  public void testDatelineBoxBoundingBoxFlag() {
    Rectangle bbox = geo3dBox(170, -170, -10, 10).getBoundingBox();
    assertTrue("wrapped representation " + describe(bbox), bbox.getMinX() > bbox.getMaxX());
    assertTrue("box 170..-170 crosses the dateline: " + describe(bbox), bbox.getCrossesDateLine());
  }

  public void testPrimeMeridianBoxBoundingBoxFlag() {
    Rectangle bbox = geo3dBox(-10, 10, -10, 10).getBoundingBox();
    assertFalse(
        "box -10..10 does not cross the dateline: " + describe(bbox), bbox.getCrossesDateLine());
  }

  public void testDatelineCircleBoundingBoxFlag() {
    Rectangle bbox = geo3dCircle(180, 0, 5).getBoundingBox();
    assertTrue(
        "bounding box of a circle around (180, 0) crosses the dateline: " + describe(bbox),
        bbox.getCrossesDateLine());
  }

  private static int count(BBoxStrategy strategy, Shape indexed, Shape query) throws Exception {
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

  public void testBBoxStrategyIndexedDatelineShape() throws Exception {
    BBoxStrategy strategy = BBoxStrategy.newInstance(CTX, "bbox");
    Shape doc = geo3dBox(170, -170, -10, 10);
    Rectangle query = CTX.getShapeFactory().rect(175, 176, -1, 1);
    assertEquals("175..176 lies inside 170..-170", 1, count(strategy, doc, query));
  }

  public void testBBoxStrategyIndexedPrimeMeridianShape() throws Exception {
    BBoxStrategy strategy = BBoxStrategy.newInstance(CTX, "bbox");
    Shape doc = geo3dBox(-10, 10, -10, 10);
    Rectangle query = CTX.getShapeFactory().rect(100, 110, -5, 5);
    assertEquals("100..110 is far from -10..10", 0, count(strategy, doc, query));
  }
}
