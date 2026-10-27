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
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.spatial.ShapeValues;
import org.apache.lucene.spatial.bbox.BBoxStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;

/**
 * Geo3dRectangleShape.reset(minX, maxX, minY, maxY) replaces the wrapped spatial3d box but leaves
 * the minX/maxX/minY/maxY fields that getMinX()..getMaxY() return unchanged.
 */
public class TestGeo3dRectangleReset extends LuceneTestCase {

  private static final SpatialContext GEO3D = new Geo3dSpatialContextFactory().newSpatialContext();

  public void testResetUpdatesCoordinates() {
    Rectangle r = GEO3D.getShapeFactory().rect(0, 0, 0, 0);
    r.reset(10, 20, 30, 40);
    // the wrapped geometry was replaced ...
    assertEquals(GEO3D.getShapeFactory().rect(10, 20, 30, 40), r);
    // ... so the coordinates must follow
    assertEquals("minX", 10, r.getMinX(), 0);
    assertEquals("maxX", 20, r.getMaxX(), 0);
    assertEquals("minY", 30, r.getMinY(), 0);
    assertEquals("maxY", 40, r.getMaxY(), 0);
  }

  /** BBoxStrategy's shape values reuse one Rectangle per segment and reset() it for every doc. */
  public void testBBoxStrategyShapeValues() throws Exception {
    BBoxStrategy strategy = BBoxStrategy.newInstance(GEO3D, "bbox");
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(GEO3D.getShapeFactory().rect(10, 20, 30, 40))) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LeafReaderContext leaf = reader.leaves().get(0);
        ShapeValues values = strategy.makeShapeValueSource().getValues(leaf);
        assertTrue(values.advanceExact(0));
        Rectangle stored = (Rectangle) values.value();
        assertEquals("minX", 10, stored.getMinX(), 0);
        assertEquals("maxX", 20, stored.getMaxX(), 0);
        assertEquals("minY", 30, stored.getMinY(), 0);
        assertEquals("maxY", 40, stored.getMaxY(), 0);
      }
    }
  }

  private static double overlapRatioScore(SpatialContext ctx) throws Exception {
    BBoxStrategy strategy = BBoxStrategy.newInstance(ctx, "bbox");
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(ctx.getShapeFactory().rect(10, 20, 30, 40))) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Rectangle query = ctx.getShapeFactory().rect(10, 20, 30, 40);
        DoubleValues v =
            strategy.makeOverlapRatioValueSource(query, 0.5).getValues(reader.leaves().get(0), null);
        assertTrue(v.advanceExact(0));
        return v.doubleValue();
      }
    }
  }

  /** The overlap-ratio relevance of a box with itself does not depend on the geo context. */
  public void testBBoxOverlapRatioScore() throws Exception {
    double spatial4jScore = overlapRatioScore(SpatialContext.GEO);
    assertTrue("reference score is positive: " + spatial4jScore, spatial4jScore > 0);
    assertEquals(spatial4jScore, overlapRatioScore(GEO3D), 1e-9);
  }
}
