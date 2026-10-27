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
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.SpatialRelation;

/**
 * Geo3dShape.relate(Rectangle) for a Rectangle that is not a Geo3dShape (for example the context's
 * own world bounds, a spatial4j RectangleImpl) returns the relation from the wrong side: WITHIN and
 * CONTAINS are swapped.
 */
public class TestGeo3dShapeRelatePlainRectangle extends LuceneTestCase {

  private static final SpatialContext GEO3D = new Geo3dSpatialContextFactory().newSpatialContext();

  public void testCircleVsWorldBounds() {
    Shape circle = GEO3D.getShapeFactory().circle(0, 0, 10);
    Rectangle world = GEO3D.getWorldBounds();
    assertFalse("world bounds are not a Geo3dShape", world instanceof Geo3dShape<?>);
    // the same rectangle built by the Geo3d shape factory is related correctly:
    assertEquals(SpatialRelation.WITHIN, circle.relate(GEO3D.getShapeFactory().rect(-180, 180, -90, 90)));
    assertEquals("a 10-degree circle is within the world", SpatialRelation.WITHIN, circle.relate(world));
    assertEquals("the world contains the circle", SpatialRelation.CONTAINS, world.relate(circle));
  }

  public void testCircleVsSmallPlainRectangle() {
    Shape circle = GEO3D.getShapeFactory().circle(0, 0, 10);
    Rectangle small =
        new org.locationtech.spatial4j.shape.impl.RectangleImpl(-1, 1, -1, 1, GEO3D);
    assertEquals(SpatialRelation.CONTAINS, circle.relate(GEO3D.getShapeFactory().rect(-1, 1, -1, 1)));
    assertEquals("the circle contains the 2x2 degree box", SpatialRelation.CONTAINS, circle.relate(small));
  }

  private static int count(SpatialOperation op, Shape query) throws Exception {
    SerializedDVStrategy strategy = new SerializedDVStrategy(GEO3D, "geom");
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(GEO3D.getShapeFactory().circle(0, 0, 10))) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        return s.count(strategy.makeQuery(new SpatialArgs(op, query)));
      }
    }
  }

  /** SerializedDVStrategy evaluates indexedShape.relate(queryShape) exactly. */
  public void testSerializedDVStrategyWorldBoundsQuery() throws Exception {
    assertEquals("IsWithin(world bounds) must match the circle", 1, count(SpatialOperation.IsWithin, GEO3D.getWorldBounds()));
    assertEquals("Contains(world bounds) must not match the circle", 0, count(SpatialOperation.Contains, GEO3D.getWorldBounds()));
  }
}
