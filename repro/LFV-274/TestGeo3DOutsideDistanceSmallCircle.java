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
 * A 1 km circle at 40N 74W. A point 3 km from the centre is about 2 km outside the circle in every
 * direction, but on WGS84 the outside distance is +Infinity in most directions.
 */
public class TestGeo3DOutsideDistanceSmallCircle extends LuceneTestCase {

  private static final double CENTER_LAT = 40.0, CENTER_LON = -74.0;
  // meters per radian; PlanetModel.SPHERE has radius 1, so use the WGS84 mean radius for both
  private static final double R = PlanetModel.WGS84.getMeanRadius();

  /** The point {@code meters} from the centre at {@code bearingDeg} (great-circle formula). */
  private static GeoPoint destination(PlanetModel pm, double bearingDeg, double meters) {
    double d = meters / R;
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

  /** Outside distances in meters of 12 points 3 km from the centre; returns the wrong ones. */
  private static String wrongOutsideDistances(PlanetModel pm) {
    GeoCircle circle =
        GeoCircleFactory.makeGeoCircle(
            pm, Math.toRadians(CENTER_LAT), Math.toRadians(CENTER_LON), 1000.0 / R);
    StringBuilder bad = new StringBuilder();
    for (int bearing = 0; bearing < 360; bearing += 30) {
      double meters =
          circle.computeOutsideDistance(DistanceStyle.ARC, destination(pm, bearing, 3000.0)) * R;
      // the edge is about 2000 m away; allow 10%
      if (!(Math.abs(meters - 2000.0) < 200.0)) {
        bad.append(" bearing ").append(bearing).append(": ").append(meters).append(" m;");
      }
    }
    return bad.toString();
  }

  public void testOutsideDistanceWGS84() {
    assertEquals(
        "outside distance should be ~2000 m:", "", wrongOutsideDistances(PlanetModel.WGS84));
  }

  /** Control: the same circle and points on the sphere. Passes. */
  public void testOutsideDistanceSphere() {
    assertEquals(
        "outside distance should be ~2000 m:", "", wrongOutsideDistances(PlanetModel.SPHERE));
  }

  /** 12 documents 21 km from the centre, about 20 km outside; doc i is at bearing 30 * i. */
  public void testOutsideDistanceSortWGS84() throws Exception {
    PlanetModel pm = PlanetModel.WGS84;
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int bearing = 0; bearing < 360; bearing += 30) {
        Document doc = new Document();
        doc.add(new Geo3DDocValuesField("point", destination(pm, bearing, 21000.0), pm));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = newSearcher(reader);
        Sort sort =
            new Sort(
                Geo3DDocValuesField.newOutsideDistanceSort(
                    "point", CENTER_LAT, CENTER_LON, 1000.0, pm));
        TopFieldDocs top = searcher.search(new MatchAllDocsQuery(), 12, sort);
        assertEquals(12, top.scoreDocs.length);
        StringBuilder all = new StringBuilder();
        int bad = 0;
        for (ScoreDoc sd : top.scoreDocs) {
          double meters = (Double) ((FieldDoc) sd).fields[0];
          all.append(" bearing ").append(30 * sd.doc).append(": ").append(meters).append(" m;");
          if (!(meters > 18000.0 && meters < 22000.0)) {
            bad++;
          }
        }
        assertEquals("sort values should all be ~20000 m, got" + all, 0, bad);
      }
    }
  }
}
