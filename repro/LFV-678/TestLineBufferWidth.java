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
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.GeohashPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.SpatialRelation;

/** Verifier probe: width of a Geo3d buffered line vs spatial4j, and an index-level search. */
public class TestLineBufferWidth extends LuceneTestCase {

  private static final SpatialContext GEO3D = new Geo3dSpatialContextFactory().newSpatialContext();

  private static Shape line(SpatialContext ctx, double buf) {
    return ctx.getShapeFactory().lineString().pointXY(0, 0).pointXY(10, 0).buffer(buf).build();
  }

  public void testWidthSweep() {
    Shape g3 = line(GEO3D, 1);
    Shape s4 = line(SpatialContext.GEO, 1);
    Shape g3circle = GEO3D.getShapeFactory().circle(5, 0, 1);
    Rectangle bb = g3.getBoundingBox();
    System.out.println("V2 geo3d line buffer(1) bbox: " + bb);
    System.out.println("V2 spatial4j line buffer(1) bbox: " + s4.getBoundingBox());
    double[] ys = {0.5, 0.9, 1.1, 2, 5, 20, 50, 57, 57.5, 58, 60};
    int g3Contains = 0;
    int s4Contains = 0;
    for (double y : ys) {
      SpatialRelation r3 = g3.relate(GEO3D.getShapeFactory().pointXY(5, y));
      SpatialRelation r4 = s4.relate(SpatialContext.GEO.getShapeFactory().pointXY(5, y));
      SpatialRelation rc = g3circle.relate(GEO3D.getShapeFactory().pointXY(5, y));
      System.out.println("V2 y=" + y + " geo3dLine=" + r3 + " spatial4jLine=" + r4 + " geo3dCircle(r=1)=" + rc);
      if (r3 != SpatialRelation.DISJOINT) g3Contains++;
      if (r4 != SpatialRelation.DISJOINT) s4Contains++;
    }
    // A radians-valued buffer of 57.2958 degrees should match the observed behaviour exactly.
    Shape g3rad = line(GEO3D, Math.toRadians(1));
    System.out.println(
        "V2 geo3d line buffer(toRadians(1)) relate (5,0.9)="
            + g3rad.relate(GEO3D.getShapeFactory().pointXY(5, 0.9))
            + " (5,1.1)="
            + g3rad.relate(GEO3D.getShapeFactory().pointXY(5, 1.1)));
    assertEquals("geo3d line contains the same sample points as spatial4j's", s4Contains, g3Contains);
  }

  public void testIndexSearch() throws Exception {
    GeohashPrefixTree grid = new GeohashPrefixTree(GEO3D, 6);
    RecursivePrefixTreeStrategy strategy = new RecursivePrefixTreeStrategy(grid, "geo");
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        double[][] pts = {{5, 0.5}, {5, 20}, {5, -40}, {100, 0}};
        for (double[] p : pts) {
          Document d = new Document();
          for (Field f : strategy.createIndexableFields(GEO3D.getShapeFactory().pointXY(p[0], p[1]))) {
            d.add(f);
          }
          d.add(new StoredField("id", p[0] + "," + p[1]));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Query q = strategy.makeQuery(new SpatialArgs(SpatialOperation.Intersects, line(GEO3D, 1)));
        int hits = s.count(q);
        System.out.println("V2 index: points {(5,0.5),(5,20),(5,-40),(100,0)}; hits for line (0,0)-(10,0) buffer(1) = " + hits);
        assertEquals("only (5,0.5) lies within 1 degree of the line", 1, hits);
      }
    }
  }
}
