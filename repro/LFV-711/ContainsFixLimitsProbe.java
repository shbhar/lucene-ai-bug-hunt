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
package org.apache.lucene.geo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LatLonShape;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.document.XYShape;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Limits of the proposed withinTriangle fixes (run on stock and with each fix).
 *
 * <p>Part L (lines): Polygon2D.withinLine has no hole test either. A document with a solid square
 * plus a line from query hole A to query hole B. Lucene's own tests define CONTAINS per geometry:
 * the query must lie inside one geometry and intersect no other geometry of the document
 * (TestXYShape / TestLatLonShape "Contains is only true if ..."), so that document must not match.
 *
 * <p>Part U (one U-shaped hole, chord between two of its corners): a real edge (3,6)-(7,6) that
 * crosses land and touches the U-shaped hole only at its two end points.
 */
public class ContainsFixLimitsProbe {

  static final float[][] SQUARE = {{0, 0, 10, 10, 0}, {0, 10, 10, 0, 0}};
  static final float[][] HOLE_A = {{1, 3, 3, 1, 1}, {4, 4, 6, 6, 4}};
  static final float[][] HOLE_B = {{7, 9, 9, 7, 7}, {4, 4, 6, 6, 4}};
  static final float[][] HOLE_U = {{1, 9, 9, 7, 7, 3, 3, 1, 1}, {3, 3, 6, 6, 4, 4, 6, 6, 3}};
  static final float[][] DOC = {{-1, -1, 11, 11, -1}, {-1, 11, 11, -1, -1}};
  // a hole of the indexed square whose only edge on land is the chord (7,6)-(3,6) of the U hole
  static final float[][] DOC_U_HOLE = {{3, 2, 2, 8, 8, 7, 3}, {6, 5, 3.5f, 3.5f, 5, 6, 6}};

  public static void main(String[] args) throws Exception {
    System.out.println("# " + (args.length > 0 ? args[0] : ""));
    Path tmp = Files.createTempDirectory("lfv711-limits");
    for (boolean latLon : new boolean[] {false, true}) {
      String t = latLon ? "LatLonShape" : "XYShape";
      // ---- Part L: polygon + line in one document, two-hole query
      try (Directory dir = FSDirectory.open(tmp.resolve(t + "-L"))) {
        try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
          addPolyLine(w, latLon, new float[] {2, 8}, new float[] {5, 5}); // 0: hole A -> hole B
          addPolyLine(w, latLon, new float[] {1.5f, 2.5f}, new float[] {5, 5}); // 1: inside A
          addPolyLine(w, latLon, new float[] {2, 5}, new float[] {5, 5}); // 2: A -> land
          addPolyLine(w, latLon, null, null); // 3: square only
        }
        try (IndexReader r = DirectoryReader.open(dir)) {
          IndexSearcher s = new IndexSearcher(r);
          System.out.printf(
              "L %-11s point (5,5) INTERSECTS                 %s%n",
              t, hits(s, point(latLon, 5, 5)));
          System.out.printf(
              "L %-11s CONTAINS two-hole query                %s   (expected [1, 3])%n",
              t, hits(s, contains(latLon, SQUARE, HOLE_A, HOLE_B)));
        }
      }
      // ---- Part U: chord between two corners of the U hole, end to end
      try (Directory dir = FSDirectory.open(tmp.resolve(t + "-U"))) {
        try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
          addPolygon(w, latLon, DOC, DOC_U_HOLE); // 0: its hole covers the land (5,5)
          addPolygon(w, latLon, DOC); // 1: solid
        }
        try (IndexReader r = DirectoryReader.open(dir)) {
          IndexSearcher s = new IndexSearcher(r);
          System.out.printf(
              "U %-11s point (5,5) INTERSECTS                 %s%n",
              t, hits(s, point(latLon, 5, 5)));
          System.out.printf(
              "U %-11s CONTAINS U-hole query                  %s   (expected [1])%n",
              t, hits(s, contains(latLon, SQUARE, HOLE_U)));
        }
      }
    }
    // ---- Part U, component level (XY): the chord as the only real edge of a triangle
    Component2D u = Polygon2D.create(xy(SQUARE, HOLE_U));
    Component2D two = Polygon2D.create(xy(SQUARE, HOLE_A, HOLE_B));
    System.out.println(
        "C U-hole   chord (3,6)-(7,6), corners of the U hole, across land: "
            + u.withinTriangle(5, 11, false, 3, 6, true, 7, 6, false)
            + "   contains(5,6)="
            + u.contains(5, 6)
            + " contains(3,6)="
            + u.contains(3, 6));
    System.out.println(
        "C two-hole chord (3,6)-(7,6), corner of A to corner of B, across land: "
            + two.withinTriangle(5, 11, false, 3, 6, true, 7, 6, false));
    System.out.println(
        "C two-hole withinLine (2,5)-(8,5), hole A -> hole B: "
            + two.withinLine(2, 8, 5, 5, 2, 5, true, 8, 5));
  }

  static void addPolyLine(IndexWriter w, boolean latLon, float[] lx, float[] ly)
      throws IOException {
    Document d = new Document();
    for (Field f :
        latLon
            ? LatLonShape.createIndexableFields("f", latLonPoly(DOC), true)
            : XYShape.createIndexableFields("f", xy(DOC), true)) {
      d.add(f);
    }
    if (lx != null) {
      for (Field f :
          latLon
              ? LatLonShape.createIndexableFields("f", new Line(toD(ly), toD(lx)))
              : XYShape.createIndexableFields("f", new XYLine(lx, ly))) {
        d.add(f);
      }
    }
    w.addDocument(d);
  }

  static void addPolygon(IndexWriter w, boolean latLon, float[][] outer, float[][]... holes)
      throws IOException {
    Document d = new Document();
    for (Field f :
        latLon
            ? LatLonShape.createIndexableFields("f", latLonPoly(outer, holes), true)
            : XYShape.createIndexableFields("f", xy(outer, holes), true)) {
      d.add(f);
    }
    w.addDocument(d);
  }

  static Query point(boolean latLon, double x, double y) {
    return latLon
        ? LatLonShape.newPointQuery("f", QueryRelation.INTERSECTS, new double[] {y, x})
        : XYShape.newPointQuery("f", QueryRelation.INTERSECTS, new float[] {(float) x, (float) y});
  }

  static Query contains(boolean latLon, float[][] outer, float[][]... holes) {
    return latLon
        ? LatLonShape.newPolygonQuery("f", QueryRelation.CONTAINS, latLonPoly(outer, holes))
        : XYShape.newPolygonQuery("f", QueryRelation.CONTAINS, xy(outer, holes));
  }

  static XYPolygon xy(float[][] outer, float[][]... holes) {
    XYPolygon[] h = new XYPolygon[holes.length];
    for (int i = 0; i < holes.length; i++) {
      h[i] = new XYPolygon(holes[i][0], holes[i][1]);
    }
    return new XYPolygon(outer[0], outer[1], h);
  }

  static Polygon latLonPoly(float[][] outer, float[][]... holes) {
    Polygon[] h = new Polygon[holes.length];
    for (int i = 0; i < holes.length; i++) {
      h[i] = new Polygon(toD(holes[i][1]), toD(holes[i][0]));
    }
    return new Polygon(toD(outer[1]), toD(outer[0]), h);
  }

  static double[] toD(float[] a) {
    double[] d = new double[a.length];
    for (int i = 0; i < a.length; i++) {
      d[i] = a[i];
    }
    return d;
  }

  static String hits(IndexSearcher s, Query q) throws IOException {
    StringBuilder b = new StringBuilder("[");
    for (ScoreDoc sd : s.search(q, 10, new Sort(SortField.FIELD_DOC)).scoreDocs) {
      if (b.length() > 1) {
        b.append(", ");
      }
      b.append(sd.doc);
    }
    return b.append("]").toString();
  }
}
