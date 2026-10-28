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
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.LatLonGeometry;
import org.apache.lucene.geo.Point;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.XYGeometry;
import org.apache.lucene.geo.XYPoint;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Reach and boundary probe for CONTAINS with holed query polygons (public API, default
 * IndexWriterConfig). Prints the CONTAINS hits per query form for XYShape and LatLonShape; exits 1
 * if doc 0 (the bridge hole, which does not contain any of the queries) is returned.
 *
 * <p>Docs, all the square [-1,11]^2 with: 0 a hole [2,8]x[4.5,5.5] (corners in the query holes);
 * 1 no hole; 2 a hole [1.5,2.5]x[4.5,5.5] inside query hole A; 3 a hole equal to query hole A
 * [1,3]x[4,6]; 4 a hole [1,2]x[4,6] that shares part of hole A's boundary. Doc 5 is the two-hole
 * query polygon itself.
 */
public class ContainsHolesReachProbe {
  static final float[][] SQ = {{0, 0, 10, 10, 0}, {0, 10, 10, 0, 0}};
  static final float[][] HA = {{1, 3, 3, 1, 1}, {4, 4, 6, 6, 4}};
  static final float[][] HB = {{7, 9, 9, 7, 7}, {4, 4, 6, 6, 4}};
  static final float[][] HU = {{1, 9, 9, 7, 7, 3, 3, 1, 1}, {3, 3, 6, 6, 4, 4, 6, 6, 3}};
  static final float[][] SUB = {{4, 4, 6, 6, 4}, {4, 6, 6, 4, 4}};
  static final float[][] SMALL = {{-0.8f, -0.8f, -0.2f, -0.2f, -0.8f}, {-0.8f, -0.2f, -0.2f, -0.8f, -0.8f}};
  static final float[][] DOC = {{-1, -1, 11, 11, -1}, {-1, 11, 11, -1, -1}};
  static final float[][] H0 = {{2, 8, 8, 2, 2}, {4.5f, 4.5f, 5.5f, 5.5f, 4.5f}};
  static final float[][] H2 = {{1.5f, 2.5f, 2.5f, 1.5f, 1.5f}, {4.5f, 4.5f, 5.5f, 5.5f, 4.5f}};
  static final float[][] H4 = {{1, 2, 2, 1, 1}, {4, 4, 6, 6, 4}};

  static XYPolygon xy(float[][] o, float[][]... h) {
    XYPolygon[] hs = new XYPolygon[h.length];
    for (int i = 0; i < h.length; i++) hs[i] = new XYPolygon(h[i][0], h[i][1]);
    return new XYPolygon(o[0], o[1], hs);
  }

  static double[] d(float[] a) {
    double[] r = new double[a.length];
    for (int i = 0; i < a.length; i++) r[i] = a[i];
    return r;
  }

  static Polygon ll(float[][] o, float[][]... h) {
    Polygon[] hs = new Polygon[h.length];
    for (int i = 0; i < h.length; i++) hs[i] = new Polygon(d(h[i][1]), d(h[i][0]));
    return new Polygon(d(o[1]), d(o[0]), hs);
  }

  static String hits(IndexSearcher s, Query q) throws Exception {
    List<Integer> l = new ArrayList<>();
    for (ScoreDoc sd : s.search(q, 20, new Sort(SortField.FIELD_DOC)).scoreDocs) l.add(sd.doc);
    return l.toString();
  }

  public static void main(String[] args) throws Exception {
    System.out.println("# " + (args.length > 0 ? args[0] : ""));
    boolean doc0Returned = false;
    for (boolean latLon : new boolean[] {false, true}) {
      float[][][][] docs = {{DOC, H0}, {DOC}, {DOC, H2}, {DOC, HA}, {DOC, H4}, {SQ, HA, HB}};
      try (Directory dir = new ByteBuffersDirectory()) {
        try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
          for (float[][][] dd : docs) {
            float[][][] holes = java.util.Arrays.copyOfRange(dd, 1, dd.length);
            Field[] fs =
                latLon
                    ? LatLonShape.createIndexableFields("f", ll(dd[0], holes), true)
                    : XYShape.createIndexableFields("f", xy(dd[0], holes), true);
            Document doc = new Document();
            for (Field f : fs) doc.add(f);
            w.addDocument(doc);
          }
        }
        try (IndexReader r = DirectoryReader.open(dir)) {
          IndexSearcher s = new IndexSearcher(r);
          String t = latLon ? "LatLonShape" : "XYShape";
          List<String[]> rows = new ArrayList<>();
          if (latLon) {
            rows.add(new String[] {"point (5,5) INTERSECTS", hits(s, LatLonShape.newPointQuery("f", QueryRelation.INTERSECTS, new double[] {5, 5}))});
            rows.add(new String[] {"CONTAINS [4,6]^2 (subset)", hits(s, LatLonShape.newPolygonQuery("f", QueryRelation.CONTAINS, ll(SUB)))});
            rows.add(new String[] {"CONTAINS two-hole query", hits(s, LatLonShape.newPolygonQuery("f", QueryRelation.CONTAINS, ll(SQ, HA, HB)))});
            rows.add(new String[] {"CONTAINS U-hole query", hits(s, LatLonShape.newPolygonQuery("f", QueryRelation.CONTAINS, ll(SQ, HU)))});
            rows.add(new String[] {"CONTAINS two-hole query + small square", hits(s, LatLonShape.newPolygonQuery("f", QueryRelation.CONTAINS, ll(SQ, HA, HB), ll(SMALL)))});
            rows.add(new String[] {"CONTAINS geometry: two-hole query + point", hits(s, LatLonShape.newGeometryQuery("f", QueryRelation.CONTAINS, new LatLonGeometry[] {ll(SQ, HA, HB), new Point(-0.5, -0.5)}))});
          } else {
            rows.add(new String[] {"point (5,5) INTERSECTS", hits(s, XYShape.newPointQuery("f", QueryRelation.INTERSECTS, new float[] {5, 5}))});
            rows.add(new String[] {"CONTAINS [4,6]^2 (subset)", hits(s, XYShape.newPolygonQuery("f", QueryRelation.CONTAINS, xy(SUB)))});
            rows.add(new String[] {"CONTAINS two-hole query", hits(s, XYShape.newPolygonQuery("f", QueryRelation.CONTAINS, xy(SQ, HA, HB)))});
            rows.add(new String[] {"CONTAINS U-hole query", hits(s, XYShape.newPolygonQuery("f", QueryRelation.CONTAINS, xy(SQ, HU)))});
            rows.add(new String[] {"CONTAINS two-hole query + small square", hits(s, XYShape.newPolygonQuery("f", QueryRelation.CONTAINS, xy(SQ, HA, HB), xy(SMALL)))});
            rows.add(new String[] {"CONTAINS geometry: two-hole query + point", hits(s, XYShape.newGeometryQuery("f", QueryRelation.CONTAINS, new XYGeometry[] {xy(SQ, HA, HB), new XYPoint(-0.5f, -0.5f)}))});
          }
          for (String[] row : rows) {
            boolean bad = row[0].startsWith("CONTAINS") && row[1].startsWith("[0");
            doc0Returned |= bad;
            System.out.printf("%-12s %-44s %-22s %s%n", t, row[0], row[1], bad ? "doc 0 WRONG" : "");
          }
        }
      }
    }
    System.exit(doc0Returned ? 1 : 0);
  }
}
