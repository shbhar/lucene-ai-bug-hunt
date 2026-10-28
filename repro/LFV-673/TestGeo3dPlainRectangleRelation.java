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
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.GeohashPrefixTree;
import org.apache.lucene.spatial.prefix.tree.PackedQuadPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.SpatialRelation;
import org.locationtech.spatial4j.shape.impl.RectangleImpl;

/**
 * In the Geo3d spatial context, a Geo3d shape must relate to a rectangle that is not a Geo3dShape
 * (the context's own world bounds, a spatial4j RectangleImpl, a PackedQuadPrefixTree cell) the same
 * way it relates to the same box built by the Geo3d shape factory.
 */
public class TestGeo3dPlainRectangleRelation extends LuceneTestCase {

  private static final SpatialContext CTX = new Geo3dSpatialContextFactory().newSpatialContext();

  public void testCircleIsWithinWorldBounds() {
    Shape circle = CTX.getShapeFactory().circle(0, 0, 10);
    Rectangle world = CTX.getWorldBounds();
    assertFalse("the world bounds are not a Geo3dShape", world instanceof Geo3dShape<?>);
    // control: the same box built by the Geo3d shape factory
    assertEquals(
        SpatialRelation.WITHIN, circle.relate(CTX.getShapeFactory().rect(-180, 180, -90, 90)));
    assertEquals(
        "a 10-degree circle is within the world", SpatialRelation.WITHIN, circle.relate(world));
    assertEquals("the world contains the circle", SpatialRelation.CONTAINS, world.relate(circle));
  }

  public void testCircleContainsSmallPlainRectangle() {
    Shape circle = CTX.getShapeFactory().circle(0, 0, 10);
    // control: the same box built by the Geo3d shape factory
    assertEquals(SpatialRelation.CONTAINS, circle.relate(CTX.getShapeFactory().rect(-1, 1, -1, 1)));
    assertEquals(
        "the circle contains the 2x2 degree box",
        SpatialRelation.CONTAINS,
        circle.relate(new RectangleImpl(-1, 1, -1, 1, CTX)));
  }

  /** SerializedDVStrategy checks indexedShape.relate(queryShape) exactly, per document. */
  public void testSerializedDVIsWithinWorldBounds() throws Exception {
    SpatialStrategy strategy = new SerializedDVStrategy(CTX, "geom");
    Shape circle = CTX.getShapeFactory().circle(0, 0, 10);
    Rectangle world = CTX.getWorldBounds();
    assertEquals(
        "IsWithin(world bounds) matches the circle",
        1,
        count(strategy, circle, SpatialOperation.IsWithin, world));
    assertEquals(
        "Contains(world bounds) does not match the circle",
        0,
        count(strategy, circle, SpatialOperation.Contains, world));
  }

  /** PackedQuadPrefixTree cells are plain spatial4j rectangles. */
  public void testPackedQuadFarPointDoesNotMatchSmallCircle() throws Exception {
    Shape circle = CTX.getShapeFactory().circle(1, 1, 0.5);
    Shape farPoint = CTX.getShapeFactory().pointXY(100.3, 37.7);
    assertEquals(SpatialRelation.DISJOINT, circle.relate(farPoint));
    // control: GeohashPrefixTree, whose cells are built by the Geo3d shape factory
    SpatialStrategy geohash = new RecursivePrefixTreeStrategy(new GeohashPrefixTree(CTX, 6), "geo");
    assertEquals(0, count(geohash, farPoint, SpatialOperation.Intersects, circle));
    SpatialStrategy packedQuad =
        new RecursivePrefixTreeStrategy(new PackedQuadPrefixTree(CTX, 12), "geo");
    assertEquals(
        "PackedQuad: a point about 97 degrees away intersects a half-degree circle",
        0,
        count(packedQuad, farPoint, SpatialOperation.Intersects, circle));
  }

  /** Indexes one document with the given shape and counts the hits of one query. */
  private static int count(
      SpatialStrategy strategy, Shape indexed, SpatialOperation op, Shape query) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(indexed)) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        return newSearcher(r).count(strategy.makeQuery(new SpatialArgs(op, query)));
      }
    }
  }
}
