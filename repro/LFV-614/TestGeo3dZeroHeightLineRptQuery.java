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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Shape;

/**
 * Geo3D spatial context with its default planet model (SPHERE), RecursivePrefixTreeStrategy over a
 * QuadPrefixTree. An Intersects query for the line of latitude 45.000000001 degrees over all
 * longitudes must not match points 5 to 35 degrees away from it. Control: the same line over
 * longitudes -179..179.
 */
public class TestGeo3dZeroHeightLineRptQuery extends LuceneTestCase {

  public void testLineQueryDoesNotMatchFarPoints() throws Exception {
    SpatialContext ctx = new Geo3dSpatialContextFactory().newSpatialContext();
    RecursivePrefixTreeStrategy strategy =
        new RecursivePrefixTreeStrategy(new QuadPrefixTree(ctx, 12), "geo");
    // {longitude, latitude}: every point is at least 5 degrees from latitude 45
    double[][] points = {{-135, 10}, {20, 10}, {170, 20}, {-100, 30}, {60, 30}, {-60, 40}};
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (double[] p : points) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(ctx.getShapeFactory().pointXY(p[0], p[1]))) {
          doc.add(f);
        }
        doc.add(new StoredField("latlon", "(" + p[1] + ", " + p[0] + ")"));
        w.addDocument(doc);
      }
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        double lat = 45.000000001;
        Shape narrower = ctx.getShapeFactory().rect(-179, 179, lat, lat);
        Shape full = ctx.getShapeFactory().rect(-180, 180, lat, lat);
        assertEquals("control: " + narrower, 0, hits(s, strategy, narrower).size());
        List<String> hits = hits(s, strategy, full);
        assertTrue(full + " matched points 5 to 35 degrees away: " + hits, hits.isEmpty());
      }
    }
  }

  private static List<String> hits(
      IndexSearcher s, RecursivePrefixTreeStrategy strategy, Shape queryShape) throws Exception {
    List<String> out = new ArrayList<>();
    SpatialArgs args = new SpatialArgs(SpatialOperation.Intersects, queryShape);
    for (ScoreDoc sd : s.search(strategy.makeQuery(args), 100).scoreDocs) {
      out.add(s.storedFields().document(sd.doc).get("latlon"));
    }
    return out;
  }
}
