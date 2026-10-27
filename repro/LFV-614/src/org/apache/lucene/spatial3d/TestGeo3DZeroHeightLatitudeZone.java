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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.spatial3d.geom.GeoBBox;
import org.apache.lucene.spatial3d.geom.GeoBBoxFactory;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.LatLonBounds;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A zero-height box that spans all longitudes (GeoBBoxFactory.makeGeoBBox(pm, lat, lat, -PI, PI), also built by
 * Geo3DPoint.newBoxQuery(field, pm, lat, lat, -180, 180)) must contain the points on its latitude, like the same box
 * with a slightly narrower longitude range does. On a non-spherical planet model it rejects them.
 */
public class TestGeo3DZeroHeightLatitudeZone extends LuceneTestCase {

  /** Geometry: the full-longitude zero-height box rejects points that lie exactly on its latitude. */
  public void testZeroHeightFullLongitudeBoxContainsPointsOnItsLatitude() {
    PlanetModel pm = PlanetModel.WGS84;
    double lat = 0.5; // radians
    GeoBBox fullLon = GeoBBoxFactory.makeGeoBBox(pm, lat, lat, -Math.PI, Math.PI);
    GeoBBox almostFullLon = GeoBBoxFactory.makeGeoBBox(pm, lat, lat, -3.0, 3.0);
    GeoPoint onLatitude = new GeoPoint(pm, lat, 1.0);

    // the box's own bounds say it lies exactly on latitude 0.5
    LatLonBounds bounds = new LatLonBounds();
    fullLon.getBounds(bounds);
    assertEquals(lat, bounds.getMinLatitude(), 1e-12);
    assertEquals(lat, bounds.getMaxLatitude(), 1e-12);

    assertTrue("control: the narrower zero-height box contains the point", almostFullLon.isWithin(onLatitude));
    assertTrue(
        fullLon + " must contain " + onLatitude + " (it lies on latitude " + lat + ")",
        fullLon.isWithin(onLatitude));
  }

  /**
   * Query: hits of the box [lat, lat] x [-179, 179] must be a subset of the hits of the box [lat, lat] x [-180,
   * 180]. The latitude is chosen so that the indexed points decode exactly onto the box's plane.
   */
  public void testFullLongitudeZeroHeightBoxQueryIsSupersetOfNarrowerBox() throws Exception {
    PlanetModel pm = PlanetModel.WGS84;
    // pick an encoded z value and the latitude whose surface points have exactly that decoded z
    double zTarget = pm.decodeValue(pm.encodeValue(0.479));
    double lo = 0.4, hi = 0.6;
    for (int i = 0; i < 200; i++) {
      double mid = (lo + hi) / 2;
      if (new GeoPoint(pm, mid, 0.0).z < zTarget) lo = mid;
      else hi = mid;
    }
    double latDegrees = Math.toDegrees((lo + hi) / 2);

    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      int n = 0;
      for (double lon = -170; lon <= 170; lon += 10) {
        Document doc = new Document();
        doc.add(new Geo3DPoint("p", pm, latDegrees, lon));
        w.addDocument(doc);
        n++;
      }
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        int narrow =
            s.count(Geo3DPoint.newBoxQuery("p", pm, latDegrees, latDegrees, -179.0, 179.0));
        int full = s.count(Geo3DPoint.newBoxQuery("p", pm, latDegrees, latDegrees, -180.0, 180.0));
        assertEquals("control: every point lies on the narrower box", n, narrow);
        assertTrue(
            "box [" + latDegrees + ", " + latDegrees + "] x [-180, 180] matched " + full
                + " points, but its subset [-179, 179] matched " + narrow,
            full >= narrow);
      }
    }
  }
}
