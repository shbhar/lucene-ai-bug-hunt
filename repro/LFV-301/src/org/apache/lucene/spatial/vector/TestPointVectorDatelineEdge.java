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
import org.locationtech.spatial4j.shape.Shape;

/**
 * In a geo context longitude -180 and +180 are the same meridian. PointVectorStrategy filters with
 * a plain range on x, so a point stored at x=+180 is not found by a rectangle (or circle) whose
 * west edge is x=-180, and a point at x=-180 is not found by a query whose east edge is +180.
 * (Circle queries go through the same bounding-box filter.)
 * spatial4j's relate (the oracle the module's tests use) and BBoxStrategy both match. Public API
 * only.
 */
public class TestPointVectorDatelineEdge extends LuceneTestCase {

  private final SpatialContext ctx = SpatialContext.GEO;

  private int count(SpatialStrategy strategy, Shape indexed, SpatialOperation op, Shape query)
      throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(indexed)) {
          doc.add(f);
        }
        writer.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        return new IndexSearcher(reader).count(strategy.makeQuery(new SpatialArgs(op, query)));
      }
    }
  }

  public void testPointAtPlus180QueryFromMinus180() throws IOException {
    Point p = ctx.getShapeFactory().pointXY(180, 0);
    Shape query = ctx.getShapeFactory().rect(-180, -170, -10, 10);
    assertTrue(SpatialOperation.Intersects.evaluate(p, query)); // spatial4j: p is on the west edge
    // BBoxStrategy (same module) treats -180 == 180 and finds it:
    assertEquals(1, count(BBoxStrategy.newInstance(ctx, "bbox"), p, SpatialOperation.Intersects, query));
    PointVectorStrategy pv = PointVectorStrategy.newInstance(ctx, "pv");
    assertEquals(1, count(pv, p, SpatialOperation.Intersects, query));
  }

  public void testPointAtMinus180QueryToPlus180() throws IOException {
    Point p = ctx.getShapeFactory().pointXY(-180, 0);
    Shape query = ctx.getShapeFactory().rect(170, 180, -10, 10);
    assertTrue(SpatialOperation.IsWithin.evaluate(p, query));
    PointVectorStrategy pv = PointVectorStrategy.newInstance(ctx, "pv");
    assertEquals(1, count(pv, p, SpatialOperation.IsWithin, query));
  }
}
