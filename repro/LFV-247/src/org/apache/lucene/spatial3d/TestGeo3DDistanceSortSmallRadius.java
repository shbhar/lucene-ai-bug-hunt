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
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Geo3DDocValuesField.newDistanceSort "orders documents by ascending distance from the location".
 * With a 1 km maximum radius, 36 documents 500 m from the centre must sort before 36 documents 3 km
 * away, and must carry a finite distance of about 500 m.
 */
public class TestGeo3DDistanceSortSmallRadius extends LuceneTestCase {

  private static final double EARTH_MEAN_RADIUS_METERS = 6371008.7714;

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

  public void testNearDocumentsSortFirst() throws Exception {
    final PlanetModel planetModel = PlanetModel.WGS84;
    final double centerLat = 40.0, centerLon = -74.0;
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      for (double meters : new double[] {500.0, 3000.0}) {
        for (int bearing = 0; bearing < 360; bearing += 10) {
          double[] ll = destination(centerLat, centerLon, bearing, meters);
          Document doc = new Document();
          doc.add(
              new Geo3DDocValuesField(
                  "point",
                  new GeoPoint(planetModel, Math.toRadians(ll[0]), Math.toRadians(ll[1])),
                  planetModel));
          doc.add(new StoredField("meters", meters));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = newSearcher(reader);
        Sort sort =
            new Sort(
                Geo3DDocValuesField.newDistanceSort(
                    "point", centerLat, centerLon, 1000.0, planetModel));
        TopFieldDocs top = searcher.search(new MatchAllDocsQuery(), 72, sort);
        int nearInFirst36 = 0, nearWithInfiniteDistance = 0;
        for (int i = 0; i < top.scoreDocs.length; i++) {
          ScoreDoc sd = top.scoreDocs[i];
          double meters =
              searcher.storedFields().document(sd.doc).getField("meters").numericValue().doubleValue();
          double sortValue = (Double) ((FieldDoc) sd).fields[0];
          if (meters == 500.0) {
            if (i < 36) {
              nearInFirst36++;
            }
            if (Double.isInfinite(sortValue)) {
              nearWithInfiniteDistance++;
            }
          }
        }
        assertTrue(
            "of the 36 documents 500 m away, only "
                + nearInFirst36
                + " sort before the documents 3 km away, and "
                + nearWithInfiniteDistance
                + " get distance +Infinity",
            nearInFirst36 == 36 && nearWithInfiniteDistance == 0);
      }
    }
  }
}
