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
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.spatial3d.geom.DistanceStyle;
import org.apache.lucene.spatial3d.geom.GeoCircle;
import org.apache.lucene.spatial3d.geom.GeoCircleFactory;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * On WGS84, the outside distance from a point 3 km from the centre of a 1 km circle must be about
 * 2 km in every direction. GeoCircle.computeOutsideDistance returns +Infinity in most directions,
 * and Geo3DDocValuesField.newOutsideDistanceSort sorts such documents as if they had no value.
 */
public class TestGeo3DOutsideDistanceSmallCircle extends LuceneTestCase {

  private static final double EARTH_MEAN_RADIUS_METERS = 6371008.7714;
  private static final double CENTER_LAT = 40.0, CENTER_LON = -74.0;

  private static GeoPoint destination(PlanetModel pm, double bearingDeg, double meters) {
    double d = meters / EARTH_MEAN_RADIUS_METERS;
    double phi1 = Math.toRadians(CENTER_LAT), lambda1 = Math.toRadians(CENTER_LON);
    double theta = Math.toRadians(bearingDeg);
    double phi2 =
        Math.asin(Math.sin(phi1) * Math.cos(d) + Math.cos(phi1) * Math.sin(d) * Math.cos(theta));
    double lambda2 =
        lambda1
            + Math.atan2(
                Math.sin(theta) * Math.sin(d) * Math.cos(phi1),
                Math.cos(d) - Math.sin(phi1) * Math.sin(phi2));
    return new GeoPoint(pm, phi2, lambda2);
  }

  public void testOutsideDistanceOnTheSurface() {
    final PlanetModel pm = PlanetModel.WGS84;
    final GeoCircle circle =
        GeoCircleFactory.makeGeoCircle(
            pm,
            Math.toRadians(CENTER_LAT),
            Math.toRadians(CENTER_LON),
            1000.0 / pm.getMeanRadius());
    StringBuilder bad = new StringBuilder();
    for (int bearing = 0; bearing < 360; bearing += 30) {
      double meters =
          circle.computeOutsideDistance(DistanceStyle.ARC, destination(pm, bearing, 3000.0))
              * pm.getMeanRadius();
      // WGS84 circles are approximate (documented up to 0.5% of the radius); allow 10% of 2 km
      if (!(Math.abs(meters - 2000.0) < 200.0)) {
        bad.append(" bearing ").append(bearing).append(": ").append(meters).append(" m;");
      }
    }
    assertEquals("outside distance should be ~2000 m:" + bad, 0, bad.length());
  }

  public void testOutsideDistanceSort() throws Exception {
    final PlanetModel pm = PlanetModel.WGS84;
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      for (int bearing = 0; bearing < 360; bearing += 30) {
        Document doc = new Document();
        doc.add(new Geo3DDocValuesField("point", destination(pm, bearing, 3000.0), pm));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = newSearcher(reader);
        Sort sort =
            new Sort(
                Geo3DDocValuesField.newOutsideDistanceSort(
                    "point", CENTER_LAT, CENTER_LON, 1000.0, pm));
        TopFieldDocs top = searcher.search(new MatchAllDocsQuery(), 12, sort);
        int infinite = 0;
        for (ScoreDoc sd : top.scoreDocs) {
          if (Double.isInfinite((Double) ((FieldDoc) sd).fields[0])) {
            infinite++;
          }
        }
        assertEquals(
            "documents 2 km outside a 1 km circle must get a finite outside distance", 0, infinite);
      }
    }
  }
}
