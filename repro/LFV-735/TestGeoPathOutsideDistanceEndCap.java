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
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial3d.geom.DistanceStyle;
import org.apache.lucene.spatial3d.geom.GeoCircle;
import org.apache.lucene.spatial3d.geom.GeoCircleFactory;
import org.apache.lucene.spatial3d.geom.GeoPath;
import org.apache.lucene.spatial3d.geom.GeoPathFactory;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * GeoOutsideDistance is "the distance from a point to the closest outside edge". A path with a
 * width measures points beside its segments that way, but points beyond its end caps (and every
 * point around a one-point path) are measured to the end point itself.
 */
public class TestGeoPathOutsideDistanceEndCap extends LuceneTestCase {

  private static final PlanetModel SPHERE = PlanetModel.SPHERE;

  /** Half-width of the paths on the unit sphere, in radians. */
  private static final double W = 0.01;

  /** A path from (0, 0) to (0, 0.1) on the unit sphere: latitude 0, longitude 0 to 0.1 rad. */
  private static GeoPath twoPointPath() {
    return GeoPathFactory.makeGeoPath(
        SPHERE, W, new GeoPoint[] {new GeoPoint(SPHERE, 0, 0), new GeoPoint(SPHERE, 0, 0.1)});
  }

  public void testOnePointPathMatchesCircle() {
    GeoPath onePointPath =
        GeoPathFactory.makeGeoPath(SPHERE, W, new GeoPoint[] {new GeoPoint(SPHERE, 0.3, 0.2)});
    GeoCircle circle = GeoCircleFactory.makeGeoCircle(SPHERE, 0.3, 0.2, W);
    GeoPoint p = new GeoPoint(SPHERE, 0.3 + W + 0.001, 0.2); // 0.001 rad outside both
    double circleDistance = circle.computeOutsideDistance(DistanceStyle.ARC, p);
    assertEquals(0.001, circleDistance, 1e-9); // control
    assertEquals(
        "a one-point path covers the same points as the circle",
        circleDistance,
        onePointPath.computeOutsideDistance(DistanceStyle.ARC, p),
        1e-9);
  }

  public void testNoJumpAtEndCapEdge() {
    GeoPath path = twoPointPath();
    GeoPoint justInside = new GeoPoint(SPHERE, 0, 0.1 + W - 1e-6);
    GeoPoint justOutside = new GeoPoint(SPHERE, 0, 0.1 + W + 1e-6);
    assertTrue(path.isWithin(justInside));
    assertFalse(path.isWithin(justOutside));
    assertEquals(0.0, path.computeOutsideDistance(DistanceStyle.ARC, justInside), 0.0); // control
    assertEquals(
        "1e-6 rad outside the end cap",
        1e-6,
        path.computeOutsideDistance(DistanceStyle.ARC, justOutside),
        1e-9);
  }

  public void testBeyondPathEnd() {
    GeoPath path = twoPointPath();
    GeoPoint beside = new GeoPoint(SPHERE, W + 0.001, 0.05); // 0.001 rad beside the segment
    assertEquals(0.001, path.computeOutsideDistance(DistanceStyle.ARC, beside), 1e-9); // control
    GeoPoint beyond = new GeoPoint(SPHERE, 0, 0.1 + W + 0.001); // 0.001 rad beyond the end cap
    assertFalse(path.isWithin(beyond));
    assertEquals(
        "0.001 rad beyond the end cap",
        0.001,
        path.computeOutsideDistance(DistanceStyle.ARC, beyond),
        1e-9);
  }

  public void testOnePointPathOnWgs84() {
    // half-width 1 km at latitude 40; the point is about 2.8 km north-east of the center
    PlanetModel pm = PlanetModel.WGS84;
    double w = 1000 / (pm.getMeanRadius() * pm.xyScaling); // as newOutsidePathSort converts it
    GeoPoint center = new GeoPoint(pm, Math.toRadians(40), Math.toRadians(10));
    GeoPath onePointPath = GeoPathFactory.makeGeoPath(pm, w, new GeoPoint[] {center});
    GeoPoint p =
        new GeoPoint(
            pm,
            center.getLatitude() + 2 * w,
            center.getLongitude() + 2 * w / Math.cos(center.getLatitude()));
    double expected = center.arcDistance(p) - w; // about 1.8 km
    double actual = onePointPath.computeOutsideDistance(DistanceStyle.ARC, p);
    assertEquals("WGS84 one-point path", expected, actual, 0.01 * expected);
  }

  public void testOutsidePathSort() throws Exception {
    // WGS84, a path along the equator from longitude 20 to 22 degrees, half-width 5 km
    PlanetModel pm = PlanetModel.WGS84;
    double[] pathLats = {0.0, 0.0};
    double[] pathLons = {20.0, 22.0};
    double halfWidthMeters = 5000; // pathWidthMeters acts as the half-width
    double kmPerDegree = 111.3; // along the equator
    double[][] docs = {
      {0.0, 22.0 + 6 / kmPerDegree}, // doc 0: 1 km beyond the east end cap
      {8 / 110.6, 21.0} // doc 1: 3 km beside the middle of the path
    };
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    for (int i = 0; i < docs.length; i++) {
      Document doc = new Document();
      doc.add(
          new Geo3DDocValuesField(
              "location",
              new GeoPoint(pm, Math.toRadians(docs[i][0]), Math.toRadians(docs[i][1])),
              pm));
      doc.add(new StoredField("id", i));
      w.addDocument(doc);
    }
    IndexReader r = w.getReader();
    w.close();
    IndexSearcher s = newSearcher(r);
    Sort sort =
        new Sort(
            Geo3DDocValuesField.newOutsidePathSort(
                "location", pathLats, pathLons, halfWidthMeters, pm));
    TopDocs td = s.search(new MatchAllDocsQuery(), 2, sort);
    int first =
        s.storedFields().document(td.scoreDocs[0].doc).getField("id").numericValue().intValue();
    double v0 = (Double) ((FieldDoc) td.scoreDocs[0]).fields[0];
    double v1 = (Double) ((FieldDoc) td.scoreDocs[1]).fields[0];
    r.close();
    dir.close();
    assertEquals(
        "the document 1 km beyond the end must sort before the one 3 km beside the path;"
            + " sort values (meters) = "
            + v0
            + ", "
            + v1,
        0,
        first);
  }
}
