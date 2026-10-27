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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial3d.geom.GeoCircle;
import org.apache.lucene.spatial3d.geom.GeoCircleFactory;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.spatial3d.geom.XYZBounds;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Geo3D distance and path sorts give every hit outside the circle (or the path) the distance
 * +Infinity, the same value as a document without the field. A second SortField must then order
 * those hits, so search(q, n, sort) must return the first n hits of the full sorted order.
 */
public class TestGeo3DDistanceSortTieAtInfinity extends LuceneTestCase {

  /**
   * One document: a location (null for none), the secondary sort key "tie", and whether the
   * location is inside the sort's circle or path (then its distance is finite).
   */
  private record Doc(GeoPoint point, int tie, boolean inShape) {}

  /** A document outside the sort's circle or path. */
  private static Doc at(PlanetModel planetModel, double latDegrees, double lonDegrees, int tie) {
    return new Doc(point(planetModel, latDegrees, lonDegrees), tie, false);
  }

  /** A document inside the sort's circle. */
  private static Doc inShape(
      PlanetModel planetModel, double latDegrees, double lonDegrees, int tie) {
    return new Doc(point(planetModel, latDegrees, lonDegrees), tie, true);
  }

  private static Doc missing(int tie) {
    return new Doc(null, tie, false);
  }

  private static GeoPoint point(PlanetModel planetModel, double latDegrees, double lonDegrees) {
    return new GeoPoint(planetModel, Math.toRadians(latDegrees), Math.toRadians(lonDegrees));
  }

  /** Five documents at latitude 60, about 6,700 km from (0, 0), with tie = doc id. */
  private static Doc[] fiveFarAway(PlanetModel planetModel) {
    Doc[] docs = new Doc[5];
    for (int i = 0; i < docs.length; i++) {
      docs[i] = at(planetModel, 60, 0, i);
    }
    return docs;
  }

  /**
   * Indexes the documents in one segment (doc ids in the given order), sorts all hits by geoSort
   * and then by "tie" descending, checks the full order of doc ids and that the top n hits are its
   * first n.
   */
  private void check(
      PlanetModel planetModel,
      SortField geoSort,
      int n,
      List<Integer> expectedFullOrder,
      Doc... docs)
      throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (Doc d : docs) {
          Document doc = new Document();
          if (d.point() != null) {
            doc.add(new Geo3DDocValuesField("loc", d.point(), planetModel));
          }
          doc.add(new NumericDocValuesField("tie", d.tie()));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        IndexSearcher searcher = new IndexSearcher(reader);
        Sort sort = new Sort(geoSort, new SortField("tie", SortField.Type.LONG, true));
        TopDocs all = searcher.search(new MatchAllDocsQuery(), docs.length + 10, sort);
        List<Integer> full = new ArrayList<>();
        for (ScoreDoc sd : all.scoreDocs) {
          full.add(sd.doc);
          // documents without the field and documents outside the shape sort as +Infinity
          double distance = (Double) ((FieldDoc) sd).fields[0];
          assertEquals(docs[sd.doc].inShape(), Double.isFinite(distance));
        }
        assertEquals("full order", expectedFullOrder, full);
        List<Integer> top = new ArrayList<>();
        for (ScoreDoc sd : searcher.search(new MatchAllDocsQuery(), n, sort).scoreDocs) {
          top.add(sd.doc);
        }
        assertEquals("top " + n + " = first " + n + " of the full order", full.subList(0, n), top);
      }
    }
  }

  /** A circle around (0, 0): 1,000 km on WGS84, 0.2 planet radii on SPHERE (mean radius 1). */
  private static SortField circleAtOrigin(PlanetModel planetModel) {
    double radius = planetModel == PlanetModel.SPHERE ? 0.2 : 1_000_000;
    return Geo3DDocValuesField.newDistanceSort("loc", 0, 0, radius, planetModel);
  }

  public void testOutsideRadiusTiesWgs84() throws Exception {
    PlanetModel pm = PlanetModel.WGS84;
    check(pm, circleAtOrigin(pm), 2, List.of(4, 3, 2, 1, 0), fiveFarAway(pm));
  }

  public void testOutsideRadiusTiesSphere() throws Exception {
    PlanetModel pm = PlanetModel.SPHERE;
    check(pm, circleAtOrigin(pm), 2, List.of(4, 3, 2, 1, 0), fiveFarAway(pm));
  }

  /** A document without the field is +Infinity too: it must tie with the out-of-radius ones. */
  public void testOutsideRadiusAndMissingTies() throws Exception {
    PlanetModel pm = PlanetModel.WGS84;
    Doc[] far = fiveFarAway(pm);
    check(
        pm,
        circleAtOrigin(pm),
        2,
        List.of(5, 4, 3, 2, 1, 0),
        far[0],
        far[1],
        far[2],
        far[3],
        far[4],
        missing(5));
  }

  /** A hit inside the radius comes first; the out-of-radius hits after it still tie. */
  public void testInsideRadiusHitThenOutsideTies() throws Exception {
    PlanetModel pm = PlanetModel.WGS84;
    Doc[] far = fiveFarAway(pm);
    Doc inside = inShape(pm, 0, 1, -1); // about 111 km from the center
    check(
        pm,
        circleAtOrigin(pm),
        3,
        List.of(5, 4, 3, 2, 1, 0),
        far[0],
        far[1],
        far[2],
        far[3],
        far[4],
        inside);
  }

  /** The path sort uses the same comparator: hits outside the path are +Infinity. */
  public void testPathSortOutsidePathTies() throws Exception {
    PlanetModel pm = PlanetModel.WGS84;
    SortField path =
        Geo3DDocValuesField.newPathSort(
            "loc", new double[] {0, 0}, new double[] {0, 1}, 50_000, pm);
    check(pm, path, 2, List.of(4, 3, 2, 1, 0), fiveFarAway(pm));
  }

  /**
   * Two out-of-radius documents, both +Infinity: one just outside the circle but inside its XYZ
   * bounding box, one far away. Both must be ordered by "tie".
   */
  public void testOutsideCircleInsideAndOutsideBoundingBoxTie() throws Exception {
    PlanetModel pm = PlanetModel.WGS84;
    double radiusRadians = 0.0157; // about 100 km around (45, 0)
    GeoCircle c = GeoCircleFactory.makeGeoCircle(pm, Math.toRadians(45), 0, radiusRadians);
    XYZBounds bounds = new XYZBounds();
    c.getBounds(bounds);
    Doc corner = at(pm, 45.8, 1.147, 2); // near a corner of the bounding box
    Doc far = at(pm, 10, 12, 3);
    assertFalse(c.isWithin(corner.point()));
    assertTrue(inside(bounds, corner.point()));
    assertFalse(inside(bounds, far.point()));
    SortField sort =
        Geo3DDocValuesField.newDistanceSort("loc", 45, 0, radiusRadians * pm.getMeanRadius(), pm);
    check(pm, sort, 2, List.of(2, 3, 1, 0), at(pm, 10, 10, 0), at(pm, 10, 11, 1), far, corner);
  }

  private static boolean inside(XYZBounds b, GeoPoint p) {
    return p.x >= b.getMinimumX()
        && p.x <= b.getMaximumX()
        && p.y >= b.getMinimumY()
        && p.y <= b.getMaximumY()
        && p.z >= b.getMinimumZ()
        && p.z <= b.getMaximumZ();
  }

  /** Control: documents without the field tie at +Infinity and "tie" orders them. */
  public void testMissingOnlyTiesControl() throws Exception {
    PlanetModel pm = PlanetModel.WGS84;
    Doc inside = inShape(pm, 0, 1, -1);
    check(
        pm,
        circleAtOrigin(pm),
        3,
        List.of(5, 4, 3, 2, 1, 0),
        missing(0),
        missing(1),
        missing(2),
        missing(3),
        missing(4),
        inside);
  }
}
