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
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial3d.geom.DistanceStyle;
import org.apache.lucene.spatial3d.geom.GeoPath;
import org.apache.lucene.spatial3d.geom.GeoPathFactory;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A path of width 20 km from (44N, 20E) to (45N, 20E) on the default WGS84 model. Points 8-16 km
 * north of the last path point are within the path's width of its end, but the path does not contain
 * them; the same points mirrored south of the first path point are contained. On PlanetModel.SPHERE
 * every one of them is contained.
 */
public class TestGeo3DPathPolewardEndCap extends LuceneTestCase {

  private static final double[] PATH_LATS = {44.0, 45.0};
  private static final double[] PATH_LONS = {20.0, 20.0};
  private static final double WIDTH_METERS = 20000;

  /** Degrees of latitude per km near 45N on WGS84 (about 111.1 km per degree), rounded up. */
  private static final double DEG_PER_KM = 1.0 / 111.0;

  // {lat, lon, km from the nearest path end point}
  private static final double[][] BEYOND_NORTH_END = {
    {45.0 + 8 * DEG_PER_KM, 20.0, 8}, {45.0 + 12 * DEG_PER_KM, 20.0, 12}, {45.0 + 16 * DEG_PER_KM, 20.0, 16}
  };
  private static final double[][] BEYOND_SOUTH_END = {
    {44.0 - 8 * DEG_PER_KM, 20.0, 8}, {44.0 - 12 * DEG_PER_KM, 20.0, 12}, {44.0 - 16 * DEG_PER_KM, 20.0, 16}
  };

  private static GeoPath path(PlanetModel planetModel) {
    // Same construction as Geo3DPoint.newPathQuery / Geo3DDocValuesField.newPathSort (Geo3DUtil.fromPath)
    GeoPoint[] points = new GeoPoint[PATH_LATS.length];
    for (int i = 0; i < points.length; i++) {
      points[i] =
          new GeoPoint(planetModel, Math.toRadians(PATH_LATS[i]), Math.toRadians(PATH_LONS[i]));
    }
    double radians = WIDTH_METERS / (planetModel.getMeanRadius() * planetModel.xyScaling);
    return GeoPathFactory.makeGeoPath(planetModel, radians, points);
  }

  public void testSphereControlContainsBothEndCaps() {
    // On the sphere the "meters" scale is the unit sphere's; use the same angular width as WGS84.
    PlanetModel pm = PlanetModel.SPHERE;
    GeoPoint[] points = {
      new GeoPoint(pm, Math.toRadians(PATH_LATS[0]), Math.toRadians(PATH_LONS[0])),
      new GeoPoint(pm, Math.toRadians(PATH_LATS[1]), Math.toRadians(PATH_LONS[1]))
    };
    double radians =
        WIDTH_METERS / (PlanetModel.WGS84.getMeanRadius() * PlanetModel.WGS84.xyScaling);
    GeoPath p = GeoPathFactory.makeGeoPath(pm, radians, points);
    for (double[] q : concat(BEYOND_NORTH_END, BEYOND_SOUTH_END)) {
      assertTrue(
          "sphere: point " + q[2] + " km beyond an end must be within the path",
          p.isWithin(new GeoPoint(pm, Math.toRadians(q[0]), Math.toRadians(q[1]))));
    }
  }

  public void testWgs84PathContainsPointsBeyondItsNorthEnd() {
    PlanetModel pm = PlanetModel.WGS84;
    GeoPath p = path(pm);
    // control: the south (equator-facing) end cap contains the mirrored points
    for (double[] q : BEYOND_SOUTH_END) {
      assertTrue(
          "control: " + q[2] + " km south of the first point is within the path",
          p.isWithin(new GeoPoint(pm, Math.toRadians(q[0]), Math.toRadians(q[1]))));
    }
    List<String> missing = new ArrayList<>();
    for (double[] q : BEYOND_NORTH_END) {
      GeoPoint gp = new GeoPoint(pm, Math.toRadians(q[0]), Math.toRadians(q[1]));
      if (p.isWithin(gp) == false) {
        missing.add(
            q[2]
                + " km north of the last point: isWithin=false, computeDistance="
                + p.computeDistance(DistanceStyle.ARC, gp));
      }
    }
    assertTrue(
        "points within " + WIDTH_METERS + " m of the path end are not in the path: " + missing,
        missing.isEmpty());
  }

  public void testPathQueryAndPathSortMissDocumentsBeyondTheNorthEnd() throws Exception {
    PlanetModel pm = PlanetModel.WGS84;
    double[][] docs = concat(BEYOND_NORTH_END, BEYOND_SOUTH_END);
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    for (int i = 0; i < docs.length; i++) {
      Document doc = new Document();
      doc.add(new Geo3DPoint("point", pm, docs[i][0], docs[i][1]));
      doc.add(
          new Geo3DDocValuesField(
              "dv", new GeoPoint(pm, Math.toRadians(docs[i][0]), Math.toRadians(docs[i][1])), pm));
      doc.add(new StoredField("id", i));
      w.addDocument(doc);
    }
    IndexReader r = w.getReader();
    w.close();
    IndexSearcher s = newSearcher(r);

    TopDocs hits =
        s.search(
            Geo3DPoint.newPathQuery("point", PATH_LATS, PATH_LONS, WIDTH_METERS, pm), docs.length);
    List<String> missed = new ArrayList<>();
    boolean[] hit = new boolean[docs.length];
    for (ScoreDoc sd : hits.scoreDocs) {
      hit[s.storedFields().document(sd.doc).getField("id").numericValue().intValue()] = true;
    }
    for (int i = 0; i < docs.length; i++) {
      if (hit[i] == false) {
        missed.add("doc " + i + " (" + docs[i][2] + " km beyond the " + (i < 3 ? "north" : "south") + " end)");
      }
    }

    // The path sort orders by distance along the path; a document inside the path has a finite value,
    // +Infinity is reserved for documents outside it (or missing the field).
    TopDocs sorted =
        s.search(
            new MatchAllDocsQuery(),
            docs.length,
            new Sort(Geo3DDocValuesField.newPathSort("dv", PATH_LATS, PATH_LONS, WIDTH_METERS, pm)));
    List<String> infinite = new ArrayList<>();
    for (ScoreDoc sd : sorted.scoreDocs) {
      double v = (Double) ((FieldDoc) sd).fields[0];
      int id = s.storedFields().document(sd.doc).getField("id").numericValue().intValue();
      if (Double.isInfinite(v)) {
        infinite.add("doc " + id + " (" + docs[id][2] + " km beyond the " + (id < 3 ? "north" : "south") + " end)");
      }
    }
    r.close();
    dir.close();
    assertTrue(
        "newPathQuery misses documents within the path width of its end: "
            + missed
            + "; newPathSort gives them +Infinity: "
            + infinite,
        missed.isEmpty() && infinite.isEmpty());
  }

  private static double[][] concat(double[][] a, double[][] b) {
    double[][] out = new double[a.length + b.length][];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(b, 0, out, a.length, b.length);
    return out;
  }
}
