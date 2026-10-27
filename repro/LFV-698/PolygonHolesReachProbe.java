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
package org.apache.lucene.document;

import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.LatLonGeometry;
import org.apache.lucene.geo.Line;
import org.apache.lucene.geo.Point;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Reach probe (public API, default IndexWriterConfig): the repro geometry (lon = x, lat = y) with
 * the holed query polygon used alone, inside a two-polygon query, and inside a mixed geometry query
 * (polygon + point). Docs: 0 = line (2,5)-(8,5) from hole A to hole B, 1 = box [2,8] x [4.5,5.5]
 * with its corners in the holes, 2 = point (5,5). All three touch the land at (5,5).
 */
public class PolygonHolesReachProbe {

  public static void main(String[] args) throws Exception {
    Polygon holeA = new Polygon(new double[] {4, 4, 6, 6, 4}, new double[] {1, 3, 3, 1, 1});
    Polygon holeB = new Polygon(new double[] {4, 4, 6, 6, 4}, new double[] {7, 9, 9, 7, 7});
    Polygon holed =
        new Polygon(new double[] {0, 10, 10, 0, 0}, new double[] {0, 0, 10, 10, 0}, holeA, holeB);
    // a second, far-away polygon, and a far-away point, that match nothing
    Polygon far =
        new Polygon(new double[] {20, 30, 30, 20, 20}, new double[] {20, 20, 30, 30, 20});
    Point farPoint = new Point(-20, -20);

    int bad = 0;
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        add(w, LatLonShape.createIndexableFields("f", new Line(new double[] {5, 5}, new double[] {2, 8})));
        add(
            w,
            LatLonShape.createIndexableFields(
                "f",
                new Polygon(new double[] {4.5, 5.5, 5.5, 4.5, 4.5}, new double[] {2, 2, 8, 8, 2})));
        add(w, LatLonShape.createIndexableFields("f", 5, 5));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        LatLonGeometry[][] queries = {{holed}, {holed, far}, {holed, farPoint}};
        String[] names = {"holed polygon alone", "holed polygon + far polygon", "holed polygon + far point"};
        for (int i = 0; i < queries.length; i++) {
          int inter = count(s, LatLonShape.newGeometryQuery("f", QueryRelation.INTERSECTS, queries[i]));
          int disj = count(s, LatLonShape.newGeometryQuery("f", QueryRelation.DISJOINT, queries[i]));
          int within = count(s, LatLonShape.newGeometryQuery("f", QueryRelation.WITHIN, queries[i]));
          boolean ok = inter == 3 && disj == 0 && within == 1;
          bad += ok ? 0 : 1;
          System.out.println(
              String.format(
                  "%-28s INTERSECTS=%d (expected 3)  DISJOINT=%d (expected 0)  WITHIN=%d (expected 1)  %s",
                  names[i], inter, disj, within, ok ? "OK" : "WRONG"));
        }
      }
    }
    System.out.println(bad == 0 ? "RESULT: all correct" : "RESULT: " + bad + " wrong");
    if (bad != 0) {
      System.exit(1);
    }
  }

  private static int count(IndexSearcher s, Query q) throws Exception {
    return s.count(q);
  }

  private static void add(IndexWriter w, Field[] fields) throws Exception {
    Document d = new Document();
    for (Field f : fields) {
      d.add(f);
    }
    w.addDocument(d);
  }
}
