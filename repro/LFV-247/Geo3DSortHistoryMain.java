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

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
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
import org.apache.lucene.store.NIOFSDirectory;

/**
 * Version-history repro (API of Lucene 8.5.0 and later: Geo3DDocValuesField and the sort factories
 * take a PlanetModel). The 6.1.0-8.4.1 variant differs only in the three API calls marked "API".
 *
 * <p>36 documents 500 m from the centre and 36 documents 3 km from the centre (WGS84, every 10
 * degrees of bearing). Under Geo3DDocValuesField.newDistanceSort with a 1 km maximum radius, every
 * 500 m document must get a finite distance and sort before every 3 km document; under
 * newOutsideDistanceSort with the same circle, every 500 m document must get distance 0.0
 * ("Points within the circle have distance 0.0"). Exits non-zero if either contract is broken.
 * Optional argument: the maximum radius in meters (default 1000; a large radius such as 50000 is a
 * control that must pass).
 */
public class Geo3DSortHistoryMain {
  private static final double EARTH_MEAN_RADIUS_METERS = 6371008.7714;
  private static final int PER_RING = 36;

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

  /** Returns the sort value of each document, indexed by docID. */
  private static double[] sortValues(IndexSearcher searcher, SortField sortField, int n)
      throws Exception {
    TopFieldDocs top = searcher.search(new MatchAllDocsQuery(), n, new Sort(sortField));
    double[] values = new double[n];
    for (ScoreDoc sd : top.scoreDocs) {
      values[sd.doc] = (Double) ((FieldDoc) sd).fields[0];
    }
    return values;
  }

  public static void main(String[] args) throws Exception {
    final double radiusMeters = args.length > 0 ? Double.parseDouble(args[0]) : 1000.0;
    final PlanetModel planetModel = PlanetModel.WGS84;
    final double centerLat = 40.0, centerLon = -74.0;
    final double[] rings = {500.0, 3000.0};
    final int n = rings.length * PER_RING;
    Path path = Files.createTempDirectory("geo3d-sort-history");
    try (Directory dir = new NIOFSDirectory(path)) {
      // Single thread, one flush at close: one segment whose docIDs follow insertion order.
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(null))) {
        for (double meters : rings) {
          for (int i = 0; i < PER_RING; i++) {
            double[] ll = destination(centerLat, centerLon, i * 10.0, meters);
            GeoPoint p = new GeoPoint(planetModel, Math.toRadians(ll[0]), Math.toRadians(ll[1]));
            Document doc = new Document();
            doc.add(new Geo3DDocValuesField("point", p, planetModel)); // API
            w.addDocument(doc);
          }
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        if (reader.leaves().size() != 1 || reader.maxDoc() != n) {
          throw new IllegalStateException(
              "SETUP: expected 1 segment with " + n + " docs, got " + reader.leaves().size()
                  + " segments, maxDoc " + reader.maxDoc());
        }
        IndexSearcher searcher = new IndexSearcher(reader);
        double[] dist =
            sortValues(
                searcher,
                Geo3DDocValuesField.newDistanceSort(
                    "point", centerLat, centerLon, radiusMeters, planetModel), // API
                n);
        double[] outside =
            sortValues(
                searcher,
                Geo3DDocValuesField.newOutsideDistanceSort(
                    "point", centerLat, centerLon, radiusMeters, planetModel), // API
                n);
        int nearInfinite = 0, nearOutsideNonZero = 0, farFinite = 0, inversions = 0;
        double nearMaxFinite = 0, worstOutside = 0;
        for (int i = 0; i < PER_RING; i++) {
          if (Double.isInfinite(dist[i])) {
            nearInfinite++;
          } else {
            nearMaxFinite = Math.max(nearMaxFinite, dist[i]);
          }
          if (outside[i] > 1.0) {
            nearOutsideNonZero++;
            worstOutside = Math.max(worstOutside, outside[i]);
          }
          for (int j = PER_RING; j < n; j++) {
            if (!(dist[i] < dist[j])) {
              inversions++;
            }
          }
        }
        for (int j = PER_RING; j < n; j++) {
          if (!Double.isInfinite(dist[j])) {
            farFinite++;
          }
        }
        System.out.println(
            "maxRadius=" + radiusMeters + " m; 500 m docs with distance +Infinity: " + nearInfinite
                + "/" + PER_RING + " (max finite " + Math.round(nearMaxFinite) + " m); 3 km docs with"
                + " a finite distance: " + farFinite + "/" + PER_RING + "; (500 m, 3 km) pairs where"
                + " the 500 m doc does not sort strictly first: " + inversions + "/" + (PER_RING * PER_RING)
                + "; 500 m docs with outside distance > 1 m: " + nearOutsideNonZero + "/" + PER_RING
                + " (worst " + Math.round(worstOutside) + " m)");
        boolean insideCircle = 500.0 < radiusMeters;
        if (insideCircle && (nearInfinite > 0 || inversions > 0 || nearOutsideNonZero > 0)) {
          throw new AssertionError(
              nearInfinite + " of " + PER_RING + " docs 500 m from the centre got distance +Infinity"
                  + " under newDistanceSort(" + radiusMeters + " m), " + inversions
                  + " (500 m, 3 km) pairs are not in ascending distance order, and "
                  + nearOutsideNonZero + " of them got an outside distance > 1 m (worst "
                  + Math.round(worstOutside) + " m) instead of 0.0");
        }
      }
    }
  }
}
