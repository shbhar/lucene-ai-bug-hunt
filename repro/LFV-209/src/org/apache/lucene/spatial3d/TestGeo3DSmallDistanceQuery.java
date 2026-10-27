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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.SloppyMath;

/**
 * Geo3DPoint.newDistanceQuery with a small radius matches points that are many times farther away
 * than the radius. The javadoc says the query matches "points within this distance" and that with
 * WGS84 it "may have up to 0.5% error".
 *
 * <p>Points are placed on two rings around the centre: one at 5 m (must all match a 10 m query)
 * and one at 100 m (must not match). Distances are checked with the haversine formula, which is
 * independent of geo3d. On main the query both misses points at 5 m and matches points at 100 m.
 */
public class TestGeo3DSmallDistanceQuery extends LuceneTestCase {

  private static final double EARTH_MEAN_RADIUS_METERS = 6371008.7714;

  /** Destination point on a sphere (degrees in, degrees out). */
  private static double[] destination(double lat, double lon, double bearingDeg, double meters) {
    double d = meters / EARTH_MEAN_RADIUS_METERS;
    double phi1 = Math.toRadians(lat), lambda1 = Math.toRadians(lon), theta = Math.toRadians(bearingDeg);
    double phi2 =
        Math.asin(Math.sin(phi1) * Math.cos(d) + Math.cos(phi1) * Math.sin(d) * Math.cos(theta));
    double lambda2 =
        lambda1
            + Math.atan2(
                Math.sin(theta) * Math.sin(d) * Math.cos(phi1),
                Math.cos(d) - Math.sin(phi1) * Math.sin(phi2));
    return new double[] {Math.toDegrees(phi2), Math.toDegrees(lambda2)};
  }

  public void testSmallRadiusDoesNotMatchFarPoints() throws Exception {
    final PlanetModel planetModel = PlanetModel.WGS84;
    final double centerLat = 40.0, centerLon = -74.0;
    final double radiusMeters = 10.0;

    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      for (double ringMeters : new double[] {5.0, 100.0}) {
        for (int bearing = 0; bearing < 360; bearing++) {
          double[] ll = destination(centerLat, centerLon, bearing, ringMeters);
          Document doc = new Document();
          doc.add(new Geo3DPoint("point", planetModel, ll[0], ll[1]));
          doc.add(new StoredField("lat", ll[0]));
          doc.add(new StoredField("lon", ll[1]));
          w.addDocument(doc);
        }
      }
      w.forceMerge(1);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = newSearcher(reader);
        Query q =
            Geo3DPoint.newDistanceQuery("point", planetModel, centerLat, centerLon, radiusMeters);
        TopDocs hits = searcher.search(q, 1000);
        int inside = 0, farHits = 0;
        double farthest = 0;
        for (ScoreDoc sd : hits.scoreDocs) {
          Document d = searcher.storedFields().document(sd.doc);
          double lat = d.getField("lat").numericValue().doubleValue();
          double lon = d.getField("lon").numericValue().doubleValue();
          double meters = SloppyMath.haversinMeters(centerLat, centerLon, lat, lon);
          farthest = Math.max(farthest, meters);
          // documented error is up to 0.5%; allow 1% plus 5 cm of quantization slack
          if (meters > radiusMeters * 1.01 + 0.05) {
            farHits++;
          } else {
            inside++;
          }
        }
        int missed = 360 - inside;
        assertTrue(
            "radius="
                + radiusMeters
                + " m query: missed "
                + missed
                + " of the 360 points 5 m from the centre, and matched "
                + farHits
                + " of the 360 points 100 m away (farthest hit "
                + farthest
                + " m)",
            missed == 0 && farHits == 0);
      }
    }
  }
}
