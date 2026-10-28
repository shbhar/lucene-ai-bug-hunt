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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial3d.geom.DistanceStyle;
import org.apache.lucene.spatial3d.geom.GeoCircle;
import org.apache.lucene.spatial3d.geom.GeoCircleFactory;
import org.apache.lucene.spatial3d.geom.GeoPath;
import org.apache.lucene.spatial3d.geom.GeoPathFactory;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * GeoOutsideDistance: "the distance from a point to the closest outside edge", 0 inside. For a point
 * beyond the end of a path, the outside distance comes out as the distance to the path's end point,
 * i.e. the true distance plus the path width.
 */
public class TestGeo3DPathOutsideDistanceEndCap extends LuceneTestCase {

  public void testOutsideDistanceBeyondPathEndOnSphere() {
    PlanetModel pm = PlanetModel.SPHERE;
    double width = 0.01; // radians
    GeoPath path =
        GeoPathFactory.makeGeoPath(
            pm, width, new GeoPoint[] {new GeoPoint(pm, 0, 0), new GeoPoint(pm, 0, 0.1)});
    // control: 0.001 rad beside the middle of the segment
    GeoPoint beside = new GeoPoint(pm, width + 0.001, 0.05);
    assertEquals(0.001, path.computeOutsideDistance(DistanceStyle.ARC, beside), 1e-9);
    // 0.001 rad beyond the end cap, on the path's own great circle: the nearest path point is the
    // tip of the end cap, 0.001 rad away
    GeoPoint beyondEnd = new GeoPoint(pm, 0, 0.1 + width + 0.001);
    assertFalse(path.isWithin(beyondEnd));
    assertEquals(
        "outside distance 0.001 rad beyond the end of a path of width " + width,
        0.001,
        path.computeOutsideDistance(DistanceStyle.ARC, beyondEnd),
        1e-9);
  }

  public void testSinglePointPathOutsideDistanceMatchesCircle() {
    PlanetModel pm = PlanetModel.SPHERE;
    double width = 0.01;
    GeoPath onePointPath =
        GeoPathFactory.makeGeoPath(pm, width, new GeoPoint[] {new GeoPoint(pm, 0.3, 0.2)});
    GeoCircle circle = GeoCircleFactory.makeGeoCircle(pm, 0.3, 0.2, width);
    GeoPoint p = new GeoPoint(pm, 0.3 + width + 0.001, 0.2);
    double circleDistance = circle.computeOutsideDistance(DistanceStyle.ARC, p);
    assertEquals(0.001, circleDistance, 1e-9);
    assertEquals(
        "a one-point path is a circle of the path width",
        circleDistance,
        onePointPath.computeOutsideDistance(DistanceStyle.ARC, p),
        1e-9);
  }

  public void testOutsidePathSortOrdersNearerDocumentLast() throws Exception {
    // Default WGS84 model, an east-west path along the equator, half-width 5 km.
    PlanetModel pm = PlanetModel.WGS84;
    double[] lats = {0.0, 0.0};
    double[] lons = {20.0, 22.0};
    double widthMeters = 5000;
    double kmPerDegree = 111.3; // along the equator
    // doc 0: 1 km beyond the east end (6 km east of the last path point)
    // doc 1: 3 km outside the path's side (8 km north of the middle of the path)
    double[][] docs = {{0.0, 22.0 + 6 / kmPerDegree}, {8 / 110.6, 21.0}};
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    for (int i = 0; i < docs.length; i++) {
      Document doc = new Document();
      doc.add(
          new Geo3DDocValuesField(
              "dv", new GeoPoint(pm, Math.toRadians(docs[i][0]), Math.toRadians(docs[i][1])), pm));
      doc.add(new StoredField("id", i));
      w.addDocument(doc);
    }
    IndexReader r = w.getReader();
    w.close();
    IndexSearcher s = newSearcher(r);
    TopDocs td =
        s.search(
            new MatchAllDocsQuery(),
            2,
            new Sort(Geo3DDocValuesField.newOutsidePathSort("dv", lats, lons, widthMeters, pm)));
    int first = s.storedFields().document(td.scoreDocs[0].doc).getField("id").numericValue().intValue();
    double v0 = (Double) ((FieldDoc) td.scoreDocs[0]).fields[0];
    double v1 = (Double) ((FieldDoc) td.scoreDocs[1]).fields[0];
    r.close();
    dir.close();
    assertEquals(
        "the document 1 km beyond the path end must sort before the one 3 km beside the path;"
            + " sort values (meters) = "
            + v0
            + ", "
            + v1,
        0,
        first);
  }
}
