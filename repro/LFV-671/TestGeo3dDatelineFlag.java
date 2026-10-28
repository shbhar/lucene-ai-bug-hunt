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
package org.apache.lucene.spatial;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.spatial.bbox.BBoxStrategy;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.spatial4j.Geo3dSpatialContextFactory;
import org.apache.lucene.spatial.vector.PointVectorStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Circle;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.SpatialRelation;

/**
 * Rectangle.getCrossesDateLine() of the Geo3d spatial context, checked against the spatial4j
 * Rectangle contract and against the default spatial4j geo context (control). Public API only.
 */
public class TestGeo3dDatelineFlag extends LuceneTestCase {

  private static SpatialContext geo3d() {
    return new Geo3dSpatialContextFactory().newSpatialContext();
  }

  private static final SpatialContext S4J = SpatialContext.GEO;

  /** Flag on a box 160..-160 (left edge 160, right edge -160) and on a box -20..30. */
  public void testFlagAgainstContract() {
    for (SpatialContext ctx : new SpatialContext[] {S4J, geo3d()}) {
      String name = ctx == S4J ? "spatial4j" : "geo3d";
      Rectangle dl = ctx.getShapeFactory().rect(160, -160, -5, 5);
      // the shape itself treats 160..-160 as a 40 degree box across the dateline
      assertEquals(name + " width", 40.0, dl.getWidth(), 1e-9);
      assertTrue(
          name + " contains lon 170",
          dl.relate(ctx.getShapeFactory().pointXY(170, 0)).intersects());
      assertFalse(
          name + " does not contain lon 0",
          dl.relate(ctx.getShapeFactory().pointXY(0, 0)).intersects());
      assertTrue(name + ": 160..-160 must cross the dateline", dl.getCrossesDateLine());

      Rectangle pm = ctx.getShapeFactory().rect(-20, 30, -5, 5);
      assertEquals(name + " width", 50.0, pm.getWidth(), 1e-9);
      assertFalse(name + ": -20..30 must not cross the dateline", pm.getCrossesDateLine());
    }
  }

  private static int count(
      SpatialContext ctx, SpatialStrategy strat, Shape[] docs, SpatialOperation op, Shape q)
      throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (Shape s : docs) {
          Document d = new Document();
          for (Field f : strat.createIndexableFields(s)) {
            d.add(f);
          }
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Query query = strat.makeQuery(new SpatialArgs(op, q));
        return new IndexSearcher(r).count(query);
      }
    }
  }

  /** PointVectorStrategy, rectangle query across the dateline. */
  public void testPointVectorRectAcrossDateline() throws Exception {
    for (SpatialContext ctx : new SpatialContext[] {S4J, geo3d()}) {
      String name = ctx == S4J ? "spatial4j" : "geo3d";
      PointVectorStrategy strat = PointVectorStrategy.newInstance(ctx, "pt");
      Shape[] docs = {
        ctx.getShapeFactory().pointXY(179, 1),
        ctx.getShapeFactory().pointXY(-179, -1),
        ctx.getShapeFactory().pointXY(0, 0)
      };
      Rectangle q = ctx.getShapeFactory().rect(160, -160, -5, 5);
      assertEquals(name + " PointVector Intersects 160..-160", 2, count(ctx, strat, docs,
          SpatialOperation.Intersects, q));
    }
  }

  /** PointVectorStrategy, circle query centred on the dateline. */
  public void testPointVectorCircleOnDateline() throws Exception {
    for (SpatialContext ctx : new SpatialContext[] {S4J, geo3d()}) {
      String name = ctx == S4J ? "spatial4j" : "geo3d";
      PointVectorStrategy strat = PointVectorStrategy.newInstance(ctx, "pt");
      Shape[] docs = {
        ctx.getShapeFactory().pointXY(179, 0), ctx.getShapeFactory().pointXY(-179, 0)
      };
      Circle c = ctx.getShapeFactory().circle(180, 0, 3);
      // the circle itself contains both points
      for (Shape p : docs) {
        assertTrue(name + " circle contains " + p, c.relate(p).intersects());
      }
      assertEquals(name + " PointVector Intersects circle(180,0,3)", 2, count(ctx, strat, docs,
          SpatialOperation.Intersects, c));
    }
  }

  /** BBoxStrategy: an indexed dateline box must be found by a query inside it. */
  public void testBBoxIndexedDatelineBox() throws Exception {
    for (SpatialContext ctx : new SpatialContext[] {S4J, geo3d()}) {
      String name = ctx == S4J ? "spatial4j" : "geo3d";
      BBoxStrategy strat = BBoxStrategy.newInstance(ctx, "bb");
      Rectangle doc = ctx.getShapeFactory().rect(160, -160, -5, 5);
      Rectangle q = ctx.getShapeFactory().rect(170, 172, -1, 1);
      assertEquals(name + " relate", SpatialRelation.CONTAINS, doc.relate(q));
      assertEquals(name + " BBox Intersects", 1, count(ctx, strat, new Shape[] {doc},
          SpatialOperation.Intersects, q));
    }
  }

  /** BBoxStrategy: an indexed box spanning lon 0 must not match a far-away query box. */
  public void testBBoxIndexedPrimeMeridianBox() throws Exception {
    for (SpatialContext ctx : new SpatialContext[] {S4J, geo3d()}) {
      String name = ctx == S4J ? "spatial4j" : "geo3d";
      BBoxStrategy strat = BBoxStrategy.newInstance(ctx, "bb");
      Rectangle doc = ctx.getShapeFactory().rect(-20, 30, -5, 5);
      Rectangle near = ctx.getShapeFactory().rect(0, 5, -1, 1);
      Rectangle far = ctx.getShapeFactory().rect(90, 100, -1, 1);
      assertEquals(name + " relate far", SpatialRelation.DISJOINT, doc.relate(far));
      assertEquals(name + " BBox Intersects near", 1, count(ctx, strat, new Shape[] {doc},
          SpatialOperation.Intersects, near));
      assertEquals(name + " BBox Intersects far", 0, count(ctx, strat, new Shape[] {doc},
          SpatialOperation.Intersects, far));
    }
  }
}
