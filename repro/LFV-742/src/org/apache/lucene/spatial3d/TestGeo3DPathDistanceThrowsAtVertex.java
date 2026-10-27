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
package org.apache.lucene.spatial3d;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial3d.geom.DistanceStyle;
import org.apache.lucene.spatial3d.geom.GeoPath;
import org.apache.lucene.spatial3d.geom.GeoPathFactory;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * GeoDistance.computeDistance returns a distance, or +Infinity outside the shape. For a point inside a
 * path that lies (within the 1e-12 plane tolerance) on the cutoff plane of an interior path point,
 * GeoStandardPath.computeDistance throws RuntimeException("Can't find world intersection ..."), and so
 * does a path sort whose path has a point at a document's (decoded) longitude.
 */
public class TestGeo3DPathDistanceThrowsAtVertex extends LuceneTestCase {

  public void testComputeDistanceOnSphere() {
    PlanetModel pm = PlanetModel.SPHERE;
    GeoPath path =
        GeoPathFactory.makeGeoPath(
            pm,
            1e-4,
            new GeoPoint[] {
              new GeoPoint(pm, 0, 0), new GeoPoint(pm, 0, 0.1), new GeoPoint(pm, 0, 0.2)
            });
    // 3.4e-6 rad beside the path, 1e-12 rad past its middle point
    GeoPoint q = new GeoPoint(pm, -3.448684587613504E-6, 0.10000000000100001);
    assertTrue(path.isWithin(q));
    double d = path.computeDistance(DistanceStyle.ARC, q);
    assertEquals(0.1 + 3.448684587613504E-6, d, 1e-9);
  }

  public void testZeroWidthPathNearestDistanceOnSphere() {
    PlanetModel pm = PlanetModel.SPHERE;
    GeoPath path =
        GeoPathFactory.makeGeoPath(
            pm,
            0.0,
            new GeoPoint[] {
              new GeoPoint(pm, 0, 0), new GeoPoint(pm, 0, 0.1), new GeoPoint(pm, 0, 0.2)
            });
    // 1e-9 rad beside the zero-width path, 1e-12 rad before its middle point
    GeoPoint q = new GeoPoint(0.9950041652781257, 0.09983341664583317, -9.999995000000419E-10);
    // the nearest centre-line point is (about) the middle point, 0.1 along the path
    assertEquals(0.1, path.computeNearestDistance(DistanceStyle.ARC, q), 1e-9);
    assertEquals(1e-9, path.computePathCenterDistance(DistanceStyle.ARC, q), 1e-7);
  }

  public void testPathSortThrows() throws Exception {
    PlanetModel pm = PlanetModel.WGS84;
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(
        new Geo3DDocValuesField(
            "dv", new GeoPoint(pm, Math.toRadians(2.0), Math.toRadians(20.0)), pm));
    w.addDocument(doc);
    IndexReader r = w.getReader();
    w.close();
    IndexSearcher s = newSearcher(r);
    // a 500 km-wide path along the equator whose middle point is at the document's decoded longitude
    double lon = 20.0000232917864;
    double[] lats = {0, 0, 0};
    double[] lons = {lon - 1, lon, lon + 1};
    try {
      TopDocs td =
          s.search(
              new MatchAllDocsQuery(),
              1,
              new Sort(Geo3DDocValuesField.newPathSort("dv", lats, lons, 500000, pm)));
      assertEquals(1, td.scoreDocs.length);
    } finally {
      r.close();
      dir.close();
    }
  }
}
