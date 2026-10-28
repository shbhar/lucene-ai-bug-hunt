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
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.spatial3d.geom.DistanceStyle;
import org.apache.lucene.spatial3d.geom.GeoBBox;
import org.apache.lucene.spatial3d.geom.GeoBBoxFactory;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A box that reaches a pole (maxLatitude 90 or minLatitude -90) contains the pole. A point on the
 * far side of the pole is therefore at most its distance to the pole away from the box. Geo3D's
 * outside distance to such a box (and so Geo3DDocValuesField.newOutsideBoxSort) measures it to a
 * bottom (top) corner instead. The last test is a control: the same box stopping just short of the
 * pole gives the right value.
 */
public class TestGeo3DPolarRectangleOutsideDistance extends LuceneTestCase {

  private static final double MEAN_RADIUS = PlanetModel.WGS84.getMeanRadius();

  private static GeoPoint point(PlanetModel pm, double latDeg, double lonDeg) {
    return new GeoPoint(pm, Math.toRadians(latDeg), Math.toRadians(lonDeg));
  }

  /** Sorts the documents (latitude, longitude) by outside distance to the box; returns the hits. */
  private TopFieldDocs sortByOutsideBoxDistance(
      double minLat, double maxLat, double minLon, double maxLon, double[][] docs)
      throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (double[] ll : docs) {
        Document doc = new Document();
        doc.add(
            new Geo3DDocValuesField(
                "loc", point(PlanetModel.WGS84, ll[0], ll[1]), PlanetModel.WGS84));
        doc.add(new StoredField("id", ll[0] + "," + ll[1]));
        w.addDocument(doc);
      }
      w.forceMerge(1);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        Sort sort =
            new Sort(
                Geo3DDocValuesField.newOutsideBoxSort(
                    "loc", minLat, maxLat, minLon, maxLon, PlanetModel.WGS84));
        return new IndexSearcher(r).search(MatchAllDocsQuery.INSTANCE, docs.length, sort);
      }
    }
  }

  private static double value(TopFieldDocs hits, int rank) {
    return (Double) ((FieldDoc) hits.scoreDocs[rank]).fields[0];
  }

  public void testNorthPolarBoxSort() throws Exception {
    // box: latitude 60..90 (contains the north pole), longitude -10..10
    // doc 0 at (80, 180): 10 degrees from the pole, which is in the box -> at most ~1,112 km
    // doc 1 at (35, 0): directly south of the box, 25 degrees from it -> ~2,780 km
    // doc 2 at the pole itself: inside (0.0 up to the doc-values encoding error of a few meters)
    double[][] docs = {{80, 180}, {35, 0}, {90, 0}};
    TopFieldDocs hits = sortByOutsideBoxDistance(60, 90, -10, 10, docs);
    // the pole document sorts first (its 21-bit doc value decodes a few meters from the pole)
    assertEquals("the pole is inside the box", 2, hits.scoreDocs[0].doc);
    assertTrue(value(hits, 0) < 10.0);
    double tenDegrees = Math.toRadians(10) * MEAN_RADIUS; // ~1,112 km
    assertEquals(
        "doc (80, 180) is 10 degrees from the pole, which the box contains, so it sorts before doc"
            + " (35, 0) (25 degrees south of the box); sort values: rank1="
            + value(hits, 1)
            + " rank2="
            + value(hits, 2),
        0,
        hits.scoreDocs[1].doc);
    assertTrue(
        "outside distance of (80, 180) must be at most its distance to the pole (~"
            + Math.round(tenDegrees)
            + " m) but was "
            + value(hits, 1),
        value(hits, 1) <= tenDegrees * 1.01);
  }

  public void testSouthPolarBoxSort() throws Exception {
    // mirror image: latitude -90..-60 (contains the south pole), longitude -10..10
    double[][] docs = {{-80, 180}, {-35, 0}, {-90, 0}};
    TopFieldDocs hits = sortByOutsideBoxDistance(-90, -60, -10, 10, docs);
    // the pole document sorts first (its 21-bit doc value decodes a few meters from the pole)
    assertEquals("the pole is inside the box", 2, hits.scoreDocs[0].doc);
    assertTrue(value(hits, 0) < 10.0);
    double tenDegrees = Math.toRadians(10) * MEAN_RADIUS;
    assertEquals(
        "doc (-80, 180) sorts before doc (-35, 0); sort values: rank1="
            + value(hits, 1)
            + " rank2="
            + value(hits, 2),
        0,
        hits.scoreDocs[1].doc);
    assertTrue(
        "outside distance of (-80, 180) must be at most ~"
            + Math.round(tenDegrees)
            + " m but was "
            + value(hits, 1),
        value(hits, 1) <= tenDegrees * 1.01);
  }

  public void testOutsideDistanceOnSphere() {
    // the shape API itself, on the unit sphere where the expected value is exact:
    // distance from (80, 180) to the box 60..90 x -10..10 = distance to the north pole = 10 degrees
    GeoBBox box =
        GeoBBoxFactory.makeGeoBBox(
            PlanetModel.SPHERE,
            Math.toRadians(90),
            Math.toRadians(60),
            Math.toRadians(-10),
            Math.toRadians(10));
    assertTrue(box.isWithin(PlanetModel.SPHERE.NORTH_POLE));
    double d = box.computeOutsideDistance(DistanceStyle.ARC, point(PlanetModel.SPHERE, 80, 180));
    assertEquals(
        "outside distance (degrees) from (80, 180) to " + box, 10.0, Math.toDegrees(d), 1e-9);
  }

  public void testControlBoxStoppingShortOfThePole() {
    // control: the same box with maxLatitude 89.9999 (about 11 m short of the pole) is a plain
    // rectangle that does not contain the pole; its outside distance from (80, 180) is just over
    // 10 degrees, as it should be
    GeoBBox box =
        GeoBBoxFactory.makeGeoBBox(
            PlanetModel.SPHERE,
            Math.toRadians(89.9999),
            Math.toRadians(60),
            Math.toRadians(-10),
            Math.toRadians(10));
    assertFalse(box.isWithin(PlanetModel.SPHERE.NORTH_POLE));
    double d = box.computeOutsideDistance(DistanceStyle.ARC, point(PlanetModel.SPHERE, 80, 180));
    assertEquals(
        "outside distance (degrees) from (80, 180) to " + box, 10.0, Math.toDegrees(d), 1e-3);
  }
}
