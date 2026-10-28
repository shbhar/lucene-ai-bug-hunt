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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.lucene.geo.Circle;
import org.apache.lucene.geo.GeoEncodingUtils;
import org.apache.lucene.geo.GeoUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.SloppyMath;

/**
 * Independent check of the radius boundary of lat/lon distance queries.
 *
 * <p>Reference: a document matches a distance query of radius r iff {@code
 * SloppyMath.haversinMeters(center, point) <= r}. This is the predicate used by the upstream
 * random test oracle (BaseGeoPointTestCase) and the value that the distance sort reports.
 */
public class TestRadiusBoundaryV1 extends LuceneTestCase {

  private static double quantLat(double lat) {
    return GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(lat));
  }

  private static double quantLon(double lon) {
    return GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(lon));
  }

  private static double randomLat(Random r) {
    return -90 + 180 * r.nextDouble();
  }

  private static double randomLon(Random r) {
    return -180 + 360 * r.nextDouble();
  }

  /**
   * Root cause in isolation. GeoUtils.distanceQuerySortKey(r) documents its result as "the exact
   * sortKey needed to match the specified radius; any sort key lte this is a query match". So for
   * a point with sort key k: k <= distanceQuerySortKey(r) must hold iff haversinMeters(k) <= r.
   */
  public void testDistanceQuerySortKeyIsAnExactThreshold() {
    Random r = random();
    int n = 2000;
    int falseNeg = 0; // radius == distance, but k > threshold
    int falsePos = 0; // radius == nextDown(distance), but k <= threshold
    int thresholdBeyondRadius = 0; // haversinMeters(threshold) > radius
    String firstFalseNeg = null;
    String firstFalsePos = null;
    for (int i = 0; i < n; i++) {
      double cLat = randomLat(r);
      double cLon = randomLon(r);
      double pLat = quantLat(randomLat(r));
      double pLon = quantLon(randomLon(r));
      double k = SloppyMath.haversinSortKey(cLat, cLon, pLat, pLon);
      double d = SloppyMath.haversinMeters(k);
      assertEquals(d, SloppyMath.haversinMeters(cLat, cLon, pLat, pLon), 0.0);
      if (d == 0) {
        continue;
      }

      double tExact = GeoUtils.distanceQuerySortKey(d);
      if ((k <= tExact) == false) {
        falseNeg++;
        if (firstFalseNeg == null) {
          // every key between the threshold and k maps to exactly the same distance d
          firstFalseNeg =
              "center=("
                  + cLat
                  + ","
                  + cLon
                  + ") point=("
                  + pLat
                  + ","
                  + pLon
                  + ") distance="
                  + d
                  + " pointSortKey="
                  + k
                  + " threshold(radius=distance)="
                  + tExact
                  + " haversinMeters(threshold)="
                  + SloppyMath.haversinMeters(tExact)
                  + " keyStepsBetween="
                  + (Double.doubleToLongBits(k) - Double.doubleToLongBits(tExact));
        }
      }

      double below = Math.nextDown(d);
      double tBelow = GeoUtils.distanceQuerySortKey(below);
      if (SloppyMath.haversinMeters(tBelow) > below) {
        thresholdBeyondRadius++;
      }
      if (k <= tBelow) {
        falsePos++;
        if (firstFalsePos == null) {
          firstFalsePos =
              "center=("
                  + cLat
                  + ","
                  + cLon
                  + ") point=("
                  + pLat
                  + ","
                  + pLon
                  + ") distance="
                  + d
                  + " radius=nextDown(distance)="
                  + below
                  + " pointSortKey="
                  + k
                  + " threshold="
                  + tBelow
                  + " haversinMeters(threshold)="
                  + SloppyMath.haversinMeters(tBelow);
        }
      }
    }
    String msg =
        "of "
            + n
            + " random (center, point) pairs: "
            + falseNeg
            + " points at exactly the radius have a sort key above distanceQuerySortKey(radius); "
            + falsePos
            + " points one ulp beyond the radius have a sort key <= distanceQuerySortKey(radius); "
            + thresholdBeyondRadius
            + " thresholds lie beyond their own radius. first false negative: "
            + firstFalseNeg
            + " ; first false positive: "
            + firstFalsePos;
    System.out.println("[v1] " + msg);
    assertTrue(msg, falseNeg == 0 && falsePos == 0);
  }

  /**
   * End to end: radius taken from a point's own haversin distance (and one ulp below it), run
   * through LatLonPoint.newDistanceQuery, LatLonDocValuesField.newSlowDistanceQuery and
   * LatLonShape.newDistanceQuery, compared with the reference predicate on every indexed doc.
   */
  public void testDistanceQueriesAtRadiusEqualToAPointDistance() throws Exception {
    Random r = random();
    int numDocs = 300;
    double[] lats = new double[numDocs];
    double[] lons = new double[numDocs];
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    for (int i = 0; i < numDocs; i++) {
      lats[i] = quantLat(randomLat(r));
      lons[i] = quantLon(randomLon(r));
      Document doc = new Document();
      doc.add(new LatLonPoint("p", lats[i], lons[i]));
      doc.add(new LatLonDocValuesField("p", lats[i], lons[i]));
      for (Field f : LatLonShape.createIndexableFields("s", lats[i], lons[i])) {
        doc.add(f);
      }
      doc.add(new StoredField("id", i));
      w.addDocument(doc);
    }
    w.forceMerge(1);
    DirectoryReader reader = DirectoryReader.open(w);
    w.close();
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setQueryCache(null);
    int[] docToId = new int[reader.maxDoc()];
    for (int d = 0; d < reader.maxDoc(); d++) {
      docToId[d] = reader.storedFields().document(d).getField("id").numericValue().intValue();
    }

    String[] names = {"LatLonPoint.newDistanceQuery", "LatLonDocValuesField.newSlowDistanceQuery",
        "LatLonShape.newDistanceQuery"};
    int[] wrongAtD = new int[3];
    int[] wrongBelowD = new int[3];
    int queries = 0;
    List<String> examples = new ArrayList<>();
    int numCenters = 4;
    for (int c = 0; c < numCenters; c++) {
      double cLat = randomLat(r);
      double cLon = randomLon(r);
      for (int src = 0; src < 60; src++) {
        double dist = SloppyMath.haversinMeters(cLat, cLon, lats[src], lons[src]);
        if (dist == 0) {
          continue;
        }
        double[] radii = {dist, Math.nextDown(dist)};
        for (int ri = 0; ri < 2; ri++) {
          double radius = radii[ri];
          queries++;
          Query[] qs = {
            LatLonPoint.newDistanceQuery("p", cLat, cLon, radius),
            LatLonDocValuesField.newSlowDistanceQuery("p", cLat, cLon, radius),
            LatLonShape.newDistanceQuery(
                "s", ShapeField.QueryRelation.INTERSECTS, new Circle(cLat, cLon, radius))
          };
          for (int qi = 0; qi < 3; qi++) {
            FixedBitSet actual = new FixedBitSet(numDocs);
            TopDocs td = searcher.search(qs[qi], numDocs);
            for (ScoreDoc sd : td.scoreDocs) {
              actual.set(docToId[sd.doc]);
            }
            int mismatches = 0;
            String ex = null;
            for (int j = 0; j < numDocs; j++) {
              boolean expected =
                  SloppyMath.haversinMeters(cLat, cLon, lats[j], lons[j]) <= radius;
              if (expected != actual.get(j)) {
                mismatches++;
                if (ex == null) {
                  ex =
                      names[qi]
                          + " center=("
                          + cLat
                          + ","
                          + cLon
                          + ") radius="
                          + radius
                          + (ri == 0 ? " (== distance of point " : " (== nextDown(distance) of point ")
                          + src
                          + ") doc point=("
                          + lats[j]
                          + ","
                          + lons[j]
                          + ") haversinMeters="
                          + SloppyMath.haversinMeters(cLat, cLon, lats[j], lons[j])
                          + " expected="
                          + expected
                          + " actual="
                          + actual.get(j);
                }
              }
            }
            if (mismatches > 0) {
              if (ri == 0) {
                wrongAtD[qi]++;
              } else {
                wrongBelowD[qi]++;
              }
              if (examples.size() < 6) {
                examples.add(ex);
              }
            }
          }
        }
      }
    }
    reader.close();
    dir.close();
    StringBuilder sb = new StringBuilder();
    sb.append("queries per factory=").append(queries).append('\n');
    for (int qi = 0; qi < 3; qi++) {
      sb.append(names[qi])
          .append(": wrong hit sets with radius==distance: ")
          .append(wrongAtD[qi])
          .append(", with radius==nextDown(distance): ")
          .append(wrongBelowD[qi])
          .append('\n');
    }
    for (String e : examples) {
      sb.append("  e.g. ").append(e).append('\n');
    }
    System.out.println("[v1] " + sb);
    int total = 0;
    for (int qi = 0; qi < 3; qi++) {
      total += wrongAtD[qi] + wrongBelowD[qi];
    }
    assertEquals(sb.toString(), 0, total);
  }

  /**
   * A natural workflow: sort by distance, take the distance of the k-th hit as reported by the
   * distance sort, then ask for all points within that distance. The k-th hit must be among them.
   */
  public void testKthNearestDistanceAsRadius() throws Exception {
    Random r = random();
    int numDocs = 500;
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    for (int i = 0; i < numDocs; i++) {
      double lat = quantLat(randomLat(r));
      double lon = quantLon(randomLon(r));
      Document doc = new Document();
      doc.add(new LatLonPoint("p", lat, lon));
      doc.add(new LatLonDocValuesField("p", lat, lon));
      w.addDocument(doc);
    }
    DirectoryReader reader = DirectoryReader.open(w);
    w.close();
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setQueryCache(null);
    int trials = 0;
    int missing = 0;
    String example = null;
    for (int c = 0; c < 20; c++) {
      double cLat = randomLat(r);
      double cLon = randomLon(r);
      TopDocs sorted =
          searcher.search(
              new MatchAllDocsQuery(),
              numDocs,
              new Sort(LatLonDocValuesField.newDistanceSort("p", cLat, cLon)));
      for (int kth = 0; kth < sorted.scoreDocs.length; kth += 25) {
        FieldDoc fd = (FieldDoc) sorted.scoreDocs[kth];
        double radius = (Double) fd.fields[0];
        trials++;
        TopDocs within =
            searcher.search(LatLonPoint.newDistanceQuery("p", cLat, cLon, radius), numDocs);
        boolean found = false;
        for (ScoreDoc sd : within.scoreDocs) {
          if (sd.doc == fd.doc) {
            found = true;
            break;
          }
        }
        if (found == false) {
          missing++;
          if (example == null) {
            example =
                "center=("
                    + cLat
                    + ","
                    + cLon
                    + ") hit #"
                    + (kth + 1)
                    + " doc="
                    + fd.doc
                    + " sort distance="
                    + radius
                    + "; distance query with that radius returned "
                    + within.scoreDocs.length
                    + " hits and not this doc";
          }
        }
      }
    }
    reader.close();
    dir.close();
    String msg =
        missing + " of " + trials + " k-th nearest hits missing from a distance query whose radius"
            + " is their own sort distance; e.g. " + example;
    System.out.println("[v1] " + msg);
    assertEquals(msg, 0, missing);
  }
}
