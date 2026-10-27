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

import java.io.IOException;
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
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.SpatialRelation;

/**
 * With the Geo3d spatial context, two shapes that only touch at one point are reported as nested
 * (WITHIN / CONTAINS) instead of INTERSECTS. Radius +/- 0.1 degree gives DISJOINT / INTERSECTS as
 * expected. Through SerializedDVStrategy (which evaluates SpatialOperation on the stored shape) an
 * IsWithin query then returns a circle that is mostly outside the query rectangle.
 */
public class TestGeo3dTangentShapesRelation extends LuceneTestCase {

  private final SpatialContext ctx = new Geo3dSpatialContextFactory().newSpatialContext();

  public void testCircleTouchingRectangleIsNotWithinIt() {
    // circle centred at lat 55, lon -135 with radius 20 degrees: its northernmost point is lat 75
    Shape circle = ctx.getShapeFactory().circle(-135, 55, 20);
    // rectangle lat [75, 90], lon 135 eastwards to -30 (crosses the dateline; contains lon -135)
    Shape rect = ctx.getShapeFactory().rect(135, -30, 75, 90);
    // the circle's centre is outside the rectangle, so the circle cannot be within it
    assertEquals(SpatialRelation.DISJOINT, rect.relate(ctx.getShapeFactory().pointXY(-135, 55)));
    assertNotSame(SpatialRelation.WITHIN, circle.relate(rect));
  }

  public void testTwoTangentEqualCirclesAreNotNested() {
    Shape c1 = ctx.getShapeFactory().circle(-20, -30, 10); // centre lat -30
    Shape c2 = ctx.getShapeFactory().circle(-20, -50, 10); // centre lat -50; they touch at lat -40
    assertEquals(SpatialRelation.DISJOINT, c1.relate(ctx.getShapeFactory().pointXY(-20, -50)));
    assertNotSame(SpatialRelation.CONTAINS, c1.relate(c2));
  }

  public void testSerializedDVIsWithinReturnsTangentCircle() throws IOException {
    SerializedDVStrategy strategy = new SerializedDVStrategy(ctx, "geom");
    Shape circle = ctx.getShapeFactory().circle(-135, 55, 20);
    Shape rect = ctx.getShapeFactory().rect(135, -30, 75, 90);
    try (Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(circle)) {
          doc.add(f);
        }
        writer.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        int hits =
            new IndexSearcher(reader)
                .count(strategy.makeQuery(new SpatialArgs(SpatialOperation.IsWithin, rect)));
        assertEquals("circle is not within the rectangle", 0, hits);
      }
    }
  }
}
