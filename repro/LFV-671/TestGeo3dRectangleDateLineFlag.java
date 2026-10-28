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
 * Rectangle.getCrossesDateLine() of a Geo3d rectangle must follow the spatial4j Rectangle contract
 * (true when the box crosses the -180/180 meridian, i.e. minX &gt; maxX), because
 * PointVectorStrategy and BBoxStrategy use it to decide whether minX..maxX wraps around. Every test
 * runs the same scenario with the default spatial4j context first (control) and then with the Geo3d
 * context.
 */
public class TestGeo3dRectangleDateLineFlag extends LuceneTestCase {

  private static final SpatialContext SPATIAL4J = SpatialContext.GEO;
  private static final SpatialContext GEO3D = new Geo3dSpatialContextFactory().newSpatialContext();
  private static final SpatialContext[] CONTEXTS = {SPATIAL4J, GEO3D};

  private static String name(SpatialContext ctx) {
    return ctx == SPATIAL4J ? "spatial4j context" : "Geo3d context";
  }

  /** 170..-170 is a 20-degree box across the dateline; -10..10 is a box around longitude 0. */
  public void testFlag() {
    for (SpatialContext ctx : CONTEXTS) {
      Rectangle dateline = ctx.getShapeFactory().rect(170, -170, -10, 10);
      Rectangle primeMeridian = ctx.getShapeFactory().rect(-10, 10, -10, 10);
      assertEquals(name(ctx) + ": width of 170..-170", 20, dateline.getWidth(), 1e-9);
      assertTrue(name(ctx) + ": 170..-170 crosses the dateline", dateline.getCrossesDateLine());
      assertFalse(
          name(ctx) + ": -10..10 does not cross the dateline", primeMeridian.getCrossesDateLine());
    }
  }

  /** PointVectorStrategy: a point at longitude 175 lies inside the box 170..-170. */
  public void testPointVectorDatelineBox() throws Exception {
    for (SpatialContext ctx : CONTEXTS) {
      Shape point = ctx.getShapeFactory().pointXY(175, 0);
      Rectangle query = ctx.getShapeFactory().rect(170, -170, -10, 10);
      assertTrue(name(ctx) + ": relate", query.relate(point).intersects());
      assertEquals(name(ctx) + ": hits", 1, count(pointVector(ctx), point, query));
    }
  }

  /** PointVectorStrategy: a point at longitude 100 lies outside the box -10..10. */
  public void testPointVectorPrimeMeridianBox() throws Exception {
    for (SpatialContext ctx : CONTEXTS) {
      Shape point = ctx.getShapeFactory().pointXY(100, 0);
      Rectangle query = ctx.getShapeFactory().rect(-10, 10, -10, 10);
      assertFalse(name(ctx) + ": relate", query.relate(point).intersects());
      assertEquals(name(ctx) + ": hits", 0, count(pointVector(ctx), point, query));
    }
  }

  /** PointVectorStrategy: a 5-degree circle around (180, 0) contains the point (-178, 1). */
  public void testPointVectorDatelineCircle() throws Exception {
    for (SpatialContext ctx : CONTEXTS) {
      Shape point = ctx.getShapeFactory().pointXY(-178, 1);
      Shape query = ctx.getShapeFactory().circle(180, 0, 5);
      assertTrue(name(ctx) + ": relate", query.relate(point).intersects());
      assertEquals(name(ctx) + ": hits", 1, count(pointVector(ctx), point, query));
    }
  }

  /** BBoxStrategy: an indexed box 170..-170 intersects the query box 175..176. */
  public void testBBoxIndexedDatelineBox() throws Exception {
    for (SpatialContext ctx : CONTEXTS) {
      Rectangle doc = ctx.getShapeFactory().rect(170, -170, -10, 10);
      Rectangle query = ctx.getShapeFactory().rect(175, 176, -1, 1);
      assertTrue(name(ctx) + ": relate", doc.relate(query).intersects());
      assertEquals(name(ctx) + ": hits", 1, count(bbox(ctx), doc, query));
    }
  }

  /** BBoxStrategy: an indexed box -10..10 does not intersect the query box 100..110. */
  public void testBBoxIndexedPrimeMeridianBox() throws Exception {
    for (SpatialContext ctx : CONTEXTS) {
      Rectangle doc = ctx.getShapeFactory().rect(-10, 10, -10, 10);
      Rectangle query = ctx.getShapeFactory().rect(100, 110, -5, 5);
      assertFalse(name(ctx) + ": relate", doc.relate(query).intersects());
      assertEquals(name(ctx) + ": hits", 0, count(bbox(ctx), doc, query));
    }
  }

  private static SpatialStrategy pointVector(SpatialContext ctx) {
    return PointVectorStrategy.newInstance(ctx, "pv");
  }

  private static SpatialStrategy bbox(SpatialContext ctx) {
    return BBoxStrategy.newInstance(ctx, "bbox");
  }

  /** Indexes one shape and counts the documents that Intersects(query) matches. */
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
}
