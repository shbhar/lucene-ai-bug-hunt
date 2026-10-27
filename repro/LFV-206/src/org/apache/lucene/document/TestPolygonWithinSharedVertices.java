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

import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.Component2D;
import org.apache.lucene.geo.GeoEncodingUtils;
import org.apache.lucene.geo.Line;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.XYGeometry;
import org.apache.lucene.geo.XYLine;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The non-convex query polygon ("dart") (0,0) (2,0) (1,1) (1,2) has its reflex vertex at (1,1). At
 * y = 1 it covers only x in [0.5, 1], so the point (1.5, 1) is outside it. The segment (2,0)-(1,2)
 * and the triangle (0,0) (2,0) (1,2) both pass through that outside region, but their vertices are
 * vertices of the dart and the dart's boundary meets them only at those vertices. Polygon2D's
 * containsLine / containsTriangle then report containment, and WITHIN queries match shapes that are
 * not within the query polygon.
 */
public class TestPolygonWithinSharedVertices extends LuceneTestCase {

  private static final XYPolygon DART =
      new XYPolygon(new float[] {0, 2, 1, 1, 0}, new float[] {0, 0, 1, 2, 0});

  private static int count(Field[] fields, Query query) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : fields) doc.add(f);
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        return new IndexSearcher(reader).count(query);
      }
    }
  }

  public void testComponentLevel() {
    Component2D dart = XYGeometry.create(DART);
    assertFalse("(1.5,1) is outside the dart", dart.contains(1.5, 1.0));
    assertFalse("(1.4,0.9) is outside the dart", dart.contains(1.4, 0.9));
    // the segment passes through (1.5, 1)
    assertFalse(dart.containsLine(2, 0, 1, 2));
    // the triangle contains (1.4, 0.9)
    assertFalse(dart.containsTriangle(0, 0, 2, 0, 1, 2));
  }

  public void testXYLineWithin() throws Exception {
    Field[] fields =
        XYShape.createIndexableFields("shape", new XYLine(new float[] {2, 1}, new float[] {0, 2}));
    assertEquals(1, count(fields, XYShape.newPolygonQuery("shape", QueryRelation.INTERSECTS, DART)));
    assertEquals(0, count(fields, XYShape.newPolygonQuery("shape", QueryRelation.WITHIN, DART)));
  }

  public void testXYPolygonWithin() throws Exception {
    Field[] fields =
        XYShape.createIndexableFields(
            "shape", new XYPolygon(new float[] {0, 2, 1, 0}, new float[] {0, 0, 2, 0}));
    assertEquals(1, count(fields, XYShape.newPolygonQuery("shape", QueryRelation.INTERSECTS, DART)));
    assertEquals(0, count(fields, XYShape.newPolygonQuery("shape", QueryRelation.WITHIN, DART)));
  }

  /**
   * LatLonShape: the same configuration about 100 m across, with every coordinate on the encoding
   * grid (as for shapes whose coordinates come from previously indexed or quantized data), so that
   * the indexed vertices coincide exactly with the query polygon's vertices.
   */
  public void testLatLonLineWithin() throws Exception {
    double s = 0.001, lat0 = 45.25, lon0 = 10.5;
    double[] lat = new double[3], lon = new double[3];
    for (int i = 0; i < 3; i++) {
      lat[i] = GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(lat0 + i * s));
      lon[i] = GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(lon0 + i * s));
    }
    // dart (lon,lat) = (0,0) (2,0) (1,1) (1,2) in units of s
    Polygon dart =
        new Polygon(
            new double[] {lat[0], lat[0], lat[1], lat[2], lat[0]},
            new double[] {lon[0], lon[2], lon[1], lon[1], lon[0]});
    // segment (2,0)-(1,2)
    Field[] fields =
        LatLonShape.createIndexableFields(
            "shape", new Line(new double[] {lat[0], lat[2]}, new double[] {lon[2], lon[1]}));
    assertEquals(
        1, count(fields, LatLonShape.newPolygonQuery("shape", QueryRelation.INTERSECTS, dart)));
    assertEquals(
        0, count(fields, LatLonShape.newPolygonQuery("shape", QueryRelation.WITHIN, dart)));
  }
}
