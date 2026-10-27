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
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Shape;

/**
 * Geo3D spatial context with its default planet model (SPHERE): an Intersects query for the zero-height rectangle
 * latitude 45.000000001, longitude -180..180 (a line) must not match points at latitudes 10 to 44. The full-longitude
 * zero-height rectangle accepts points up to 1e-10 off its plane in isWithin, so a prefix-tree cell whose top-left
 * corner lies on latitude 45 is judged to be inside the line (GeoArea.CONTAINS) and every document in it matches.
 */
public class TestGeo3dZeroHeightRectPrefixTreeQuery extends LuceneTestCase {

  public void testZeroHeightRectangleQueryMatchesOnlyNearItsLatitude() throws Exception {
    SpatialContext ctx = new Geo3dSpatialContextFactory().newSpatialContext();
    RecursivePrefixTreeStrategy strategy =
        new RecursivePrefixTreeStrategy(new QuadPrefixTree(ctx, 12), "geo");
    double[][] points = {{-135, 10}, {-100, 30}, {-170, 44}, {20, 10}};
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (double[] p : points) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(ctx.getShapeFactory().pointXY(p[0], p[1]))) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        double lat = 45.000000001;
        Shape almostFull = ctx.getShapeFactory().rect(-179, 179, lat, lat);
        Shape full = ctx.getShapeFactory().rect(-180, 180, lat, lat);
        int almostFullHits =
            s.count(strategy.makeQuery(new SpatialArgs(SpatialOperation.Intersects, almostFull)));
        int fullHits = s.count(strategy.makeQuery(new SpatialArgs(SpatialOperation.Intersects, full)));
        assertEquals("control: " + almostFull, 0, almostFullHits);
        assertEquals(
            full + " matched points 1 to 35 degrees away from its latitude", 0, fullHits);
      }
    }
  }
}
