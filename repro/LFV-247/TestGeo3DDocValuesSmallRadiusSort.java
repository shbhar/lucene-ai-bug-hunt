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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Geo3DDocValuesField distance sorts with a small maximum radius. 36 documents lie 500 m from the
 * centre and 36 lie 3 km away, one every 10 degrees of bearing. With a 1 km maximum radius the 500
 * m documents are well inside the circle: newDistanceSort must sort them first with their distance
 * in meters, and newOutsideDistanceSort must give them 0.0.
 */
public class TestGeo3DDocValuesSmallRadiusSort extends LuceneTestCase {

  private static final PlanetModel PLANET = PlanetModel.WGS84;
  private static final double CENTER_LAT = 40.0;
  private static final double CENTER_LON = -74.0;
  private static final double NEAR = 500.0;
  private static final double FAR = 3000.0;

  /** Point at the given distance and bearing from (lat, lon), on a sphere of mean radius. */
  private static double[] destination(double lat, double lon, double bearingDeg, double meters) {
    double d = meters / PLANET.getMeanRadius();
    double phi1 = Math.toRadians(lat);
    double lambda1 = Math.toRadians(lon);
    double theta = Math.toRadians(bearingDeg);
    double phi2 =
        Math.asin(Math.sin(phi1) * Math.cos(d) + Math.cos(phi1) * Math.sin(d) * Math.cos(theta));
    double lambda2 =
        lambda1
            + Math.atan2(
                Math.sin(theta) * Math.sin(d) * Math.cos(phi1),
                Math.cos(d) - Math.sin(phi1) * Math.sin(phi2));
    return new double[] {phi2, lambda2};
  }

  /** Returns {distance from the centre in meters, sort value} for all 72 hits, in sort order. */
  private static double[][] sortAll(SortField sortField) throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      for (double meters : new double[] {NEAR, FAR}) {
        for (int bearing = 0; bearing < 360; bearing += 10) {
          double[] latLon = destination(CENTER_LAT, CENTER_LON, bearing, meters);
          Document doc = new Document();
          doc.add(
              new Geo3DDocValuesField("point", new GeoPoint(PLANET, latLon[0], latLon[1]), PLANET));
          doc.add(new StoredField("meters", meters));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = newSearcher(reader);
        TopFieldDocs top = searcher.search(new MatchAllDocsQuery(), 72, new Sort(sortField));
        StoredFields storedFields = searcher.storedFields();
        double[][] hits = new double[top.scoreDocs.length][];
        for (int i = 0; i < hits.length; i++) {
          ScoreDoc sd = top.scoreDocs[i];
          double meters =
              storedFields.document(sd.doc).getField("meters").numericValue().doubleValue();
          hits[i] = new double[] {meters, (Double) ((FieldDoc) sd).fields[0]};
        }
        return hits;
      }
    }
  }

  private static void assertNearDocumentsFirst(String label, double[][] hits) {
    assertEquals(72, hits.length);
    int nearFirst = 0, nearBeforeFar = 0, nearInfinite = 0, nearWrongValue = 0, farFinite = 0;
    boolean farSeen = false;
    for (int i = 0; i < hits.length; i++) {
      double meters = hits[i][0];
      double value = hits[i][1];
      if (meters == NEAR) {
        if (i < 36) {
          nearFirst++;
        }
        if (farSeen == false) {
          nearBeforeFar++;
        }
        if (Double.isInfinite(value)) {
          nearInfinite++;
        } else if (Math.abs(value - NEAR) > 25) {
          nearWrongValue++;
        }
      } else {
        farSeen = true;
        if (Double.isFinite(value)) {
          farFinite++;
        }
      }
    }
    assertTrue(
        label
            + ": only "
            + nearFirst
            + " of the first 36 hits are documents 500 m away ("
            + nearBeforeFar
            + " sort before every document 3 km away); of the 36 documents 500 m away, "
            + nearInfinite
            + " get distance +Infinity and "
            + nearWrongValue
            + " a distance more than 25 m off; "
            + farFinite
            + " of the 36 documents 3 km away get a finite distance",
        nearFirst == 36 && nearInfinite == 0 && nearWrongValue == 0);
  }

  public void testDistanceSortSmallRadius() throws Exception {
    assertNearDocumentsFirst(
        "newDistanceSort, 1 km",
        sortAll(
            Geo3DDocValuesField.newDistanceSort("point", CENTER_LAT, CENTER_LON, 1000, PLANET)));
  }

  public void testOutsideDistanceSortSmallRadius() throws Exception {
    double[][] hits =
        sortAll(
            Geo3DDocValuesField.newOutsideDistanceSort(
                "point", CENTER_LAT, CENTER_LON, 1000, PLANET));
    int nearNonZero = 0;
    double worst = 0;
    for (double[] hit : hits) {
      if (hit[0] == NEAR && hit[1] > 1) {
        nearNonZero++;
        worst = Math.max(worst, hit[1]);
      }
    }
    assertEquals(
        "newOutsideDistanceSort, 1 km: "
            + nearNonZero
            + " of the 36 documents 500 m inside the circle get an outside distance above 1 m"
            + " (worst "
            + Math.round(worst)
            + " m) instead of 0.0",
        0,
        nearNonZero);
  }

  /** Control: the same documents and a 50 km maximum radius sort correctly. */
  public void testDistanceSortLargeRadius() throws Exception {
    assertNearDocumentsFirst(
        "newDistanceSort, 50 km",
        sortAll(
            Geo3DDocValuesField.newDistanceSort("point", CENTER_LAT, CENTER_LON, 50000, PLANET)));
  }
}
