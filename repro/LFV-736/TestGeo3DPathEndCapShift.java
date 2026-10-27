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
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A 1 km wide path from (44N, 20E) to (45N, 20E) on WGS84, queried through Geo3DPoint.newPathQuery
 * and sorted with Geo3DDocValuesField.newPathSort. Both end caps are wrong: the path matches
 * documents 10-40 km south of its first point (far outside its width) and misses a document 0.5 km
 * north of its last point (inside its width). Distances are ellipsoidal surface distances from
 * PlanetModel.surfacePointOnBearing.
 */
public class TestGeo3DPathEndCapShift extends LuceneTestCase {

  private static final double[] LATS = {44.0, 45.0};
  private static final double[] LONS = {20.0, 20.0};
  private static final double WIDTH_METERS = 1000;

  public void testBothEndCapsOfANorthSouthPath() throws Exception {
    PlanetModel pm = PlanetModel.WGS84;
    double m = pm.getMeanRadius();
    GeoPoint start = new GeoPoint(pm, Math.toRadians(LATS[0]), Math.toRadians(LONS[0]));
    GeoPoint end = new GeoPoint(pm, Math.toRadians(LATS[1]), Math.toRadians(LONS[1]));
    // {point, meters from the nearest path point, expected to match}
    List<Object[]> docs = new ArrayList<>();
    for (double km : new double[] {10, 20, 30, 40}) {
      docs.add(new Object[] {pm.surfacePointOnBearing(start, km * 1000 / m, Math.PI), km + " km south of the first point", false});
    }
    docs.add(new Object[] {pm.surfacePointOnBearing(end, 500 / m, 0), "0.5 km north of the last point", true});

    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    for (int i = 0; i < docs.size(); i++) {
      GeoPoint p = (GeoPoint) docs.get(i)[0];
      Document doc = new Document();
      doc.add(new Geo3DPoint("point", pm, Math.toDegrees(p.getLatitude()), Math.toDegrees(p.getLongitude())));
      doc.add(new Geo3DDocValuesField("dv", p, pm));
      doc.add(new StoredField("id", i));
      w.addDocument(doc);
    }
    IndexReader r = w.getReader();
    w.close();
    IndexSearcher s = newSearcher(r);

    boolean[] hit = new boolean[docs.size()];
    TopDocs hits = s.search(Geo3DPoint.newPathQuery("point", LATS, LONS, WIDTH_METERS, pm), docs.size());
    for (ScoreDoc sd : hits.scoreDocs) {
      hit[s.storedFields().document(sd.doc).getField("id").numericValue().intValue()] = true;
    }
    double[] sortValue = new double[docs.size()];
    TopDocs sorted =
        s.search(
            new MatchAllDocsQuery(),
            docs.size(),
            new Sort(Geo3DDocValuesField.newPathSort("dv", LATS, LONS, WIDTH_METERS, pm)));
    for (ScoreDoc sd : sorted.scoreDocs) {
      sortValue[s.storedFields().document(sd.doc).getField("id").numericValue().intValue()] =
          (Double) ((FieldDoc) sd).fields[0];
    }
    r.close();
    dir.close();

    List<String> wrong = new ArrayList<>();
    for (int i = 0; i < docs.size(); i++) {
      boolean expected = (Boolean) docs.get(i)[2];
      if (hit[i] != expected) {
        wrong.add(
            docs.get(i)[1]
                + ": newPathQuery "
                + (hit[i] ? "matches" : "misses")
                + " it, newPathSort value "
                + sortValue[i]);
      }
    }
    assertTrue(
        "path of width " + WIDTH_METERS + " m from (44N,20E) to (45N,20E) on WGS84: " + wrong,
        wrong.isEmpty());
  }
}
