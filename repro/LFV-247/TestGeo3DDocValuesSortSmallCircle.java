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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.spatial3d.geom.GeoCircle;
import org.apache.lucene.spatial3d.geom.GeoCircleFactory;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Geo3DDocValuesField distance sorts with a small maximum radius.
 *
 * <p>Documents are placed on rings around a centre (default WGS84 planet model). The reference
 * distance of a document is the arc distance between the centre and the document's exact
 * (unencoded) surface point, times the planet's mean radius: exactly what the sort would report if
 * doc values stored the point without loss. Documents on inner rings lie far inside the circle (at
 * most 70% of the radius), so the encoding loss of a few metres cannot move them across the rim.
 *
 * <p>Only public API is used.
 */
@LuceneTestCase.SuppressSysoutChecks(bugUrl = "prints a one-line summary per check")
public class TestGeo3DDocValuesSortSmallCircle extends LuceneTestCase {

  private static final PlanetModel PM = PlanetModel.WGS84;
  private static final String DV = "dv";
  private static final String PT = "pt";
  private static final int BEARINGS = 36;

  /** Centres (degrees): mid latitude, equator, southern hemisphere, high latitude. */
  private static final double[][] CENTRES = {
    {48.85, 2.35}, {0.5, -60.25}, {-33.9, 151.2}, {64.1, -21.9}
  };

  /** Destination on a sphere: start (deg), bearing (deg), distance (m). Returns {lat, lon} deg. */
  private static double[] destination(
      double latDeg, double lonDeg, double bearingDeg, double meters) {
    double d = meters / PM.getMeanRadius();
    double lat0 = Math.toRadians(latDeg);
    double lon0 = Math.toRadians(lonDeg);
    double b = Math.toRadians(bearingDeg);
    double lat = Math.asin(Math.sin(lat0) * Math.cos(d) + Math.cos(lat0) * Math.sin(d) * Math.cos(b));
    double lon =
        lon0
            + Math.atan2(
                Math.sin(b) * Math.sin(d) * Math.cos(lat0),
                Math.cos(d) - Math.sin(lat0) * Math.sin(lat));
    return new double[] {Math.toDegrees(lat), Math.toDegrees(lon)};
  }

  /** One indexed document: its exact point and reference distance from the centre (m). */
  private static final class Doc {
    final GeoPoint exact;
    final double refMeters;
    final double ringMeters;

    Doc(GeoPoint exact, double refMeters, double ringMeters) {
      this.exact = exact;
      this.refMeters = refMeters;
      this.ringMeters = ringMeters;
    }
  }

  private static final class Fixture implements AutoCloseable {
    final Directory dir;
    final IndexReader reader;
    final IndexSearcher searcher;
    final List<Doc> docs;

    Fixture(Directory dir, IndexReader reader, IndexSearcher searcher, List<Doc> docs) {
      this.dir = dir;
      this.reader = reader;
      this.searcher = searcher;
      this.docs = docs;
    }

    @Override
    public void close() throws Exception {
      reader.close();
      dir.close();
    }
  }

  private Fixture build(double cLat, double cLon, double[] rings) throws Exception {
    GeoPoint centre = new GeoPoint(PM, Math.toRadians(cLat), Math.toRadians(cLon));
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    List<Doc> docs = new ArrayList<>();
    for (double ring : rings) {
      for (int i = 0; i < BEARINGS; i++) {
        double[] ll = destination(cLat, cLon, i * (360.0 / BEARINGS), ring);
        GeoPoint p = new GeoPoint(PM, Math.toRadians(ll[0]), Math.toRadians(ll[1]));
        Document doc = new Document();
        doc.add(new StoredField("id", docs.size()));
        doc.add(new Geo3DDocValuesField(DV, p, PM));
        doc.add(new Geo3DPoint(PT, PM, ll[0], ll[1]));
        w.addDocument(doc);
        docs.add(new Doc(p, centre.arcDistance(p) * PM.getMeanRadius(), ring));
      }
    }
    IndexReader r = w.getReader();
    w.close();
    return new Fixture(dir, r, newSearcher(r), docs);
  }

  /** Returns, per doc id, {sort value, rank} for the sorted search. */
  private static double[][] sortedValues(Fixture f, Query q, SortField sf) throws Exception {
    int n = f.docs.size();
    TopFieldDocs td = f.searcher.search(q, n, new Sort(sf));
    StoredFields sfs = f.reader.storedFields();
    double[][] out = new double[n][];
    for (int rank = 0; rank < td.scoreDocs.length; rank++) {
      ScoreDoc sd = td.scoreDocs[rank];
      int id = sfs.document(sd.doc).getField("id").numericValue().intValue();
      out[id] = new double[] {(Double) ((FieldDoc) sd).fields[0], rank};
    }
    return out;
  }

  /**
   * newDistanceSort: every document at most 0.7 * maxRadius from the centre must get a finite sort
   * value close to its reference distance and must sort before every document at >= 2 *
   * maxRadius.
   */
  private String checkDistanceSort(double radius, StringBuilder log) throws Exception {
    double[] rings = {0.1 * radius, 0.3 * radius, 0.5 * radius, 0.7 * radius, 2 * radius, 3 * radius};
    int inside = 0, insideInf = 0, insideOff = 0, outsideFinite = 0, orderViolations = 0;
    double worstOff = 0, farthestFinite = 0;
    for (double[] c : CENTRES) {
      try (Fixture f = build(c[0], c[1], rings)) {
        GeoCircle shape =
            GeoCircleFactory.makeGeoCircle(
                PM, Math.toRadians(c[0]), Math.toRadians(c[1]), radius / PM.getMeanRadius());
        double[][] v =
            sortedValues(
                f,
                new MatchAllDocsQuery(),
                Geo3DDocValuesField.newDistanceSort(DV, c[0], c[1], radius, PM));
        double lastInsideRank = -1, firstOutsideRank = Double.MAX_VALUE;
        for (int id = 0; id < f.docs.size(); id++) {
          Doc d = f.docs.get(id);
          double val = v[id][0];
          if (d.ringMeters <= 0.7 * radius + 1e-9) {
            // test sanity: the exact point is inside the very circle the sort builds
            assertTrue("SANITY: exact point not within circle", shape.isWithin(d.exact));
            inside++;
            lastInsideRank = Math.max(lastInsideRank, v[id][1]);
            if (Double.isInfinite(val)) {
              insideInf++;
            } else if (Math.abs(val - d.refMeters) > 25.0) {
              insideOff++;
              worstOff = Math.max(worstOff, Math.abs(val - d.refMeters));
            }
          } else {
            firstOutsideRank = Math.min(firstOutsideRank, v[id][1]);
            if (Double.isFinite(val)) {
              outsideFinite++;
              farthestFinite = Math.max(farthestFinite, d.refMeters);
            }
          }
        }
        if (lastInsideRank > firstOutsideRank) {
          orderViolations++;
        }
      }
    }
    String s =
        String.format(
            java.util.Locale.ROOT,
            "newDistanceSort maxRadius=%.0f m: %d docs at <=0.7*radius, %d of them got +Infinity,"
                + " %d finite but >25 m off (worst %.1f m); %d docs at >=2*radius got finite values"
                + " (farthest %.0f m); centres where an inside doc sorted after an outside doc: %d/%d",
            radius,
            inside,
            insideInf,
            insideOff,
            worstOff,
            outsideFinite,
            farthestFinite,
            orderViolations,
            CENTRES.length);
    log.append(s).append('\n');
    System.out.println(s);
    return (insideInf == 0 && insideOff == 0 && orderViolations == 0) ? null : s;
  }

  /** newOutsideDistanceSort: docs at <= 0.7 * radius are within the circle: distance 0.0. */
  private String checkOutsideDistanceSort(double radius, StringBuilder log) throws Exception {
    double[] rings = {0.1 * radius, 0.3 * radius, 0.5 * radius, 0.7 * radius};
    int inside = 0, nonZero = 0;
    double worst = 0;
    for (double[] c : CENTRES) {
      try (Fixture f = build(c[0], c[1], rings)) {
        double[][] v =
            sortedValues(
                f,
                new MatchAllDocsQuery(),
                Geo3DDocValuesField.newOutsideDistanceSort(DV, c[0], c[1], radius, PM));
        for (int id = 0; id < f.docs.size(); id++) {
          inside++;
          double val = v[id][0];
          if (!(val <= 1.0)) {
            nonZero++;
            worst = Math.max(worst, val);
          }
        }
      }
    }
    String s =
        String.format(
            java.util.Locale.ROOT,
            "newOutsideDistanceSort maxRadius=%.0f m: %d docs at <=0.7*radius, %d of them got an"
                + " outside distance > 1 m (worst %.1f m) instead of 0.0",
            radius,
            inside,
            nonZero,
            worst);
    log.append(s).append('\n');
    System.out.println(s);
    return nonZero == 0 ? null : s;
  }

  public void testDistanceSortOneKm() throws Exception {
    String err = checkDistanceSort(1000, new StringBuilder());
    if (err != null) {
      fail(err);
    }
  }

  public void testOutsideDistanceSortOneKm() throws Exception {
    String err = checkOutsideDistanceSort(1000, new StringBuilder());
    if (err != null) {
      fail(err);
    }
  }

  /**
   * Realistic use: filter with Geo3DPoint.newDistanceQuery and sort the hits with
   * Geo3DDocValuesField.newDistanceSort over the same circle. Every hit matched the circle, so every
   * hit must get a finite distance <= the radius (plus a few metres of encoding loss).
   */
  public void testQueryHitsGetFiniteSortValue() throws Exception {
    double radius = 1000;
    double[] rings = {100, 300, 500, 700, 2000, 3000};
    int hits = 0, hitsInf = 0, expectedHits = 0;
    for (double[] c : CENTRES) {
      try (Fixture f = build(c[0], c[1], rings)) {
        Query q = Geo3DPoint.newDistanceQuery(PT, PM, c[0], c[1], radius);
        for (Doc d : f.docs) {
          if (d.ringMeters <= 700) expectedHits++;
        }
        TopFieldDocs td =
            f.searcher.search(
                q,
                f.docs.size(),
                new Sort(Geo3DDocValuesField.newDistanceSort(DV, c[0], c[1], radius, PM)));
        for (ScoreDoc sd : td.scoreDocs) {
          hits++;
          double val = (Double) ((FieldDoc) sd).fields[0];
          if (!(val <= radius + 25.0)) {
            hitsInf++;
          }
        }
      }
    }
    String s =
        String.format(
            java.util.Locale.ROOT,
            "Geo3DPoint.newDistanceQuery(1 km) matched %d docs (expected %d);"
                + " newDistanceSort(1 km) gave %d of those hits +Infinity or a value > radius",
            hits,
            expectedHits,
            hitsInf);
    System.out.println(s);
    assertEquals("SANITY: query hit count", expectedHits, hits);
    assertEquals(s, 0, hitsInf);
  }

  /** Control: the same checks pass with a large maximum radius (50 km). */
  public void testControlLargeRadius() throws Exception {
    StringBuilder log = new StringBuilder();
    String e1 = checkDistanceSort(50_000, log);
    String e2 = checkOutsideDistanceSort(50_000, log);
    assertNull(log.toString(), e1);
    assertNull(log.toString(), e2);
  }

  /** Not an assertion: prints how the effect scales with the radius. */
  public void testSweepPrintOnly() throws Exception {
    StringBuilder log = new StringBuilder();
    for (double radius : new double[] {250, 500, 1000, 2000, 5000, 10_000, 20_000}) {
      checkDistanceSort(radius, log);
      checkOutsideDistanceSort(radius, log);
    }
  }
}
