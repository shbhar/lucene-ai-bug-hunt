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
import java.util.Locale;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A 20 km path due north along 20E, from (44N, 20E) to (45N, 20E), queried with {@link
 * Geo3DPoint#newPathQuery} and sorted with {@link Geo3DDocValuesField#newPathSort}. Documents lie
 * on the same meridian beyond the two ends; a document should match exactly when its surface
 * distance to the nearest path end ({@link PlanetModel#surfaceDistance}) is at most the width.
 */
public class TestGeo3DPathEndCaps extends LuceneTestCase {

  private static final double[] LATS = {44.0, 45.0};
  private static final double[] LONS = {20.0, 20.0};
  private static final double WIDTH_METERS = 20000;

  /**
   * Documents north of the last point and south of the first point, in km (111.1 km = 1 degree).
   */
  private static final double[] KM_NORTH_OF_END = {5, 12, 16};

  private static final double[] KM_SOUTH_OF_START = {10, 30, 40};

  public void testWgs84() throws Exception {
    List<String> wrong = wrongDocuments(PlanetModel.WGS84);
    assertTrue("WGS84, width " + WIDTH_METERS + " m: " + wrong, wrong.isEmpty());
  }

  /** Control: a sphere with WGS84's mean radius, so that the width is still in meters. */
  public void testSphere() throws Exception {
    double r = PlanetModel.WGS84.getMeanRadius();
    List<String> wrong = wrongDocuments(new PlanetModel(r, r));
    assertTrue("sphere, width " + WIDTH_METERS + " m: " + wrong, wrong.isEmpty());
  }

  private static List<String> wrongDocuments(PlanetModel pm) throws Exception {
    GeoPoint start = new GeoPoint(pm, Math.toRadians(LATS[0]), Math.toRadians(LONS[0]));
    GeoPoint end = new GeoPoint(pm, Math.toRadians(LATS[1]), Math.toRadians(LONS[1]));
    List<GeoPoint> points = new ArrayList<>();
    List<String> labels = new ArrayList<>();
    List<Double> meters = new ArrayList<>();
    for (double km : KM_NORTH_OF_END) {
      GeoPoint p = new GeoPoint(pm, Math.toRadians(LATS[1] + km / 111.1), Math.toRadians(20));
      points.add(p);
      meters.add(pm.surfaceDistance(end, p) * pm.getMeanRadius());
      labels.add("north of the last point");
    }
    for (double km : KM_SOUTH_OF_START) {
      GeoPoint p = new GeoPoint(pm, Math.toRadians(LATS[0] - km / 111.1), Math.toRadians(20));
      points.add(p);
      meters.add(pm.surfaceDistance(start, p) * pm.getMeanRadius());
      labels.add("south of the first point");
    }

    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    for (int i = 0; i < points.size(); i++) {
      GeoPoint p = points.get(i);
      Document doc = new Document();
      doc.add(
          new Geo3DPoint(
              "point", pm, Math.toDegrees(p.getLatitude()), Math.toDegrees(p.getLongitude())));
      doc.add(new Geo3DDocValuesField("dv", p, pm));
      doc.add(new StoredField("id", i));
      w.addDocument(doc);
    }
    IndexReader r = w.getReader();
    w.close();
    IndexSearcher s = newSearcher(r);

    boolean[] matched = new boolean[points.size()];
    TopDocs hits =
        s.search(Geo3DPoint.newPathQuery("point", LATS, LONS, WIDTH_METERS, pm), points.size());
    for (ScoreDoc sd : hits.scoreDocs) {
      matched[s.storedFields().document(sd.doc).getField("id").numericValue().intValue()] = true;
    }
    double[] sortValue = new double[points.size()];
    TopDocs sorted =
        s.search(
            MatchAllDocsQuery.INSTANCE,
            points.size(),
            new Sort(Geo3DDocValuesField.newPathSort("dv", LATS, LONS, WIDTH_METERS, pm)));
    for (ScoreDoc sd : sorted.scoreDocs) {
      int id = s.storedFields().document(sd.doc).getField("id").numericValue().intValue();
      sortValue[id] = (Double) ((FieldDoc) sd).fields[0];
    }
    r.close();
    dir.close();

    List<String> wrong = new ArrayList<>();
    for (int i = 0; i < points.size(); i++) {
      boolean expected = meters.get(i) <= WIDTH_METERS;
      if (matched[i] != expected) {
        wrong.add(
            String.format(
                Locale.ROOT,
                "%.1f km %s: %s, newPathSort %.0f",
                meters.get(i) / 1000,
                labels.get(i),
                matched[i] ? "matched" : "missed",
                sortValue[i]));
      }
    }
    return wrong;
  }
}
