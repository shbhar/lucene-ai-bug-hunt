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
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.Tessellator;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: a valid polygon whose two holes each touch the same outer edge at one point.
 *
 * <p>Shell (clockwise): (0,0) (6,10) (12,0). Hole A (counter-clockwise): (2,0) (3,2) (2,3); its
 * left-most vertex (2,0) lies on the outer edge y = 0. Hole B (counter-clockwise): (5,0) (7,1)
 * (5,1); its left-most vertex (5,0) lies on the same outer edge. Between x = 2 and x = 5 the edge
 * y = 0 is polygon boundary. The box x in [3,4], y in [-0.5,0.3] leaves the polygon through that
 * piece of boundary, so the polygon does not contain it; the box x in [3,4], y in [0.05,0.3] is
 * inside the polygon and outside both holes.
 *
 * <p>Variants: the same shell with explicit vertices at (2,0) and (5,0); every start vertex of the
 * shell; both hole orders; a second shape of hole B; single-hole controls.
 */
@LuceneTestCase.SuppressSysoutChecks(bugUrl = "prints one diagnostic line per case")
public class TestTwoHolesOnOuterEdgeContains extends LuceneTestCase {

  // shell without vertices at the touch points (clockwise)
  static final double[][] SHELL_PLAIN = {{0, 0}, {6, 10}, {12, 0}};
  // shell with explicit (collinear) vertices at both touch points (clockwise)
  static final double[][] SHELL_EXPLICIT = {{0, 0}, {6, 10}, {12, 0}, {5, 0}, {2, 0}};
  // holes (counter-clockwise)
  static final double[][] HOLE_A = {{2, 0}, {3, 2}, {2, 3}};
  static final double[][] HOLE_B = {{5, 0}, {7, 1}, {5, 1}};
  static final double[][] HOLE_B2 = {{5, 0}, {8, 2}, {6, 2}};

  static double[][] rotate(double[][] ring, int start) {
    double[][] r = new double[ring.length + 1][];
    for (int i = 0; i < ring.length; i++) {
      r[i] = ring[(start + i) % ring.length];
    }
    r[ring.length] = r[0];
    return r;
  }

  static double[] xs(double[][] closed) {
    double[] v = new double[closed.length];
    for (int i = 0; i < closed.length; i++) v[i] = closed[i][0];
    return v;
  }

  static double[] ys(double[][] closed) {
    double[] v = new double[closed.length];
    for (int i = 0; i < closed.length; i++) v[i] = closed[i][1];
    return v;
  }

  static float[] f(double[] d) {
    float[] v = new float[d.length];
    for (int i = 0; i < d.length; i++) v[i] = (float) d[i];
    return v;
  }

  static XYPolygon xy(double[][] closed, XYPolygon... holes) {
    return new XYPolygon(f(xs(closed)), f(ys(closed)), holes);
  }

  static Polygon latLon(double[][] closed, Polygon... holes) {
    // lat = y, lon = x
    return new Polygon(ys(closed), xs(closed), holes);
  }

  /** a case: the shell (unrotated), the list of holes in insertion order, a label */
  static final class Case {
    final double[][] shell;
    final double[][][] holes;
    final String label;

    Case(double[][] shell, double[][][] holes, String label) {
      this.shell = shell;
      this.holes = holes;
      this.label = label;
    }
  }

  static List<Case> twoHoleCases() {
    List<Case> cases = new ArrayList<>();
    double[][][] shells = {SHELL_PLAIN, SHELL_EXPLICIT};
    String[] shellNames = {"plain shell", "shell with touch vertices"};
    double[][][] bShapes = {HOLE_B, HOLE_B2};
    String[] bNames = {"B", "B2"};
    for (int s = 0; s < shells.length; s++) {
      for (int b = 0; b < bShapes.length; b++) {
        for (int start = 0; start < shells[s].length; start++) {
          double[][] shell = rotate(shells[s], start);
          String base =
              shellNames[s] + " start (" + fmt(shell[0]) + "), holes A+" + bNames[b] + " order ";
          cases.add(
              new Case(
                  shell, new double[][][] {rotate(HOLE_A, 0), rotate(bShapes[b], 0)}, base + "AB"));
          cases.add(
              new Case(
                  shell, new double[][][] {rotate(bShapes[b], 0), rotate(HOLE_A, 0)}, base + "BA"));
        }
      }
    }
    return cases;
  }

  static List<Case> singleHoleControls() {
    List<Case> cases = new ArrayList<>();
    double[][][] shells = {SHELL_PLAIN, SHELL_EXPLICIT};
    String[] shellNames = {"plain shell", "shell with touch vertices"};
    for (int s = 0; s < shells.length; s++) {
      for (int start = 0; start < shells[s].length; start++) {
        double[][] shell = rotate(shells[s], start);
        cases.add(
            new Case(
                shell,
                new double[][][] {rotate(HOLE_A, 0)},
                shellNames[s] + " start (" + fmt(shell[0]) + "), hole A only"));
        cases.add(
            new Case(
                shell,
                new double[][][] {rotate(HOLE_B, 0)},
                shellNames[s] + " start (" + fmt(shell[0]) + "), hole B only"));
      }
    }
    return cases;
  }

  static String fmt(double[] p) {
    return (p[0] == Math.rint(p[0]) ? Long.toString((long) p[0]) : Double.toString(p[0]))
        + ","
        + (p[1] == Math.rint(p[1]) ? Long.toString((long) p[1]) : Double.toString(p[1]));
  }

  static XYPolygon toXY(Case c) {
    XYPolygon[] holes = new XYPolygon[c.holes.length];
    for (int i = 0; i < holes.length; i++) holes[i] = xy(c.holes[i]);
    return xy(c.shell, holes);
  }

  static Polygon toLatLon(Case c) {
    Polygon[] holes = new Polygon[c.holes.length];
    for (int i = 0; i < holes.length; i++) holes[i] = latLon(c.holes[i]);
    return latLon(c.shell, holes);
  }

  // ---- oracle for the edge flags: an edge belongs to the polygon iff both ends lie on one ring
  // segment (of the shell or of a hole)
  static boolean onSegment(double ax, double ay, double bx, double by, double px, double py) {
    double cross = (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    if (cross != 0) return false;
    return Math.min(ax, bx) <= px
        && px <= Math.max(ax, bx)
        && Math.min(ay, by) <= py
        && py <= Math.max(ay, by);
  }

  static boolean edgeOnRing(double[][] closed, double px, double py, double qx, double qy) {
    for (int i = 0; i + 1 < closed.length; i++) {
      double ax = closed[i][0], ay = closed[i][1], bx = closed[i + 1][0], by = closed[i + 1][1];
      if (onSegment(ax, ay, bx, by, px, py) && onSegment(ax, ay, bx, by, qx, qy)) return true;
    }
    return false;
  }

  // Both shells have the same boundary point set; the plain shell's three straight sides are the
  // maximal straight pieces, so an edge lies on the shell boundary iff both ends lie on one of them.
  static final double[][] SHELL_ORACLE = rotate(SHELL_PLAIN, 0);

  static boolean edgeOnPolygon(Case c, double px, double py, double qx, double qy) {
    if (edgeOnRing(SHELL_ORACLE, px, py, qx, qy)) return true;
    for (double[][] h : c.holes) {
      if (edgeOnRing(h, px, py, qx, qy)) return true;
    }
    return false;
  }

  static List<String> flagMismatches(Case c, List<Tessellator.Triangle> tris) {
    List<String> bad = new ArrayList<>();
    for (Tessellator.Triangle t : tris) {
      for (int e = 0; e < 3; e++) {
        int n = (e + 1) % 3;
        boolean expected = edgeOnPolygon(c, t.getX(e), t.getY(e), t.getX(n), t.getY(n));
        if (t.isEdgefromPolygon(e) != expected) {
          bad.add(
              "triangle "
                  + t
                  + " edge ("
                  + t.getX(e)
                  + ","
                  + t.getY(e)
                  + ")-("
                  + t.getX(n)
                  + ","
                  + t.getY(n)
                  + ") flag="
                  + t.isEdgefromPolygon(e)
                  + " expected="
                  + expected);
        }
      }
    }
    return bad;
  }

  // ---- end-to-end CONTAINS counts
  int[] xyContainsCounts(XYPolygon polygon) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field fld : XYShape.createIndexableFields("shape", polygon)) doc.add(fld);
        w.addDocument(doc);
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        int out =
            s.count(XYShape.newBoxQuery("shape", QueryRelation.CONTAINS, 3f, 4f, -0.5f, 0.3f));
        int in =
            s.count(XYShape.newBoxQuery("shape", QueryRelation.CONTAINS, 3f, 4f, 0.05f, 0.3f));
        int outIntersects =
            s.count(XYShape.newBoxQuery("shape", QueryRelation.INTERSECTS, 3f, 4f, -0.5f, 0.3f));
        return new int[] {out, in, outIntersects};
      }
    }
  }

  int[] latLonContainsCounts(Polygon polygon) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field fld : LatLonShape.createIndexableFields("shape", polygon)) doc.add(fld);
        w.addDocument(doc);
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        // newBoxQuery(field, relation, minLat, maxLat, minLon, maxLon)
        int out = s.count(LatLonShape.newBoxQuery("shape", QueryRelation.CONTAINS, -0.5, 0.3, 3, 4));
        int in = s.count(LatLonShape.newBoxQuery("shape", QueryRelation.CONTAINS, 0.05, 0.3, 3, 4));
        int outIntersects =
            s.count(LatLonShape.newBoxQuery("shape", QueryRelation.INTERSECTS, -0.5, 0.3, 3, 4));
        return new int[] {out, in, outIntersects};
      }
    }
  }

  List<String> runContains(List<Case> cases, boolean latLon) throws Exception {
    List<String> bad = new ArrayList<>();
    for (Case c : cases) {
      int[] counts = latLon ? latLonContainsCounts(toLatLon(c)) : xyContainsCounts(toXY(c));
      String line =
          c.label
              + ": CONTAINS(box sticking out)="
              + counts[0]
              + " CONTAINS(box inside)="
              + counts[1]
              + " INTERSECTS(box sticking out)="
              + counts[2];
      System.out.println((latLon ? "[LatLon] " : "[XY] ") + line);
      if (counts[0] != 0 || counts[1] != 1 || counts[2] != 1) bad.add(line);
    }
    return bad;
  }

  List<String> runFlags(List<Case> cases, boolean latLon) {
    List<String> bad = new ArrayList<>();
    for (Case c : cases) {
      List<Tessellator.Triangle> tris =
          latLon
              ? Tessellator.tessellate(toLatLon(c), random().nextBoolean())
              : Tessellator.tessellate(toXY(c), random().nextBoolean());
      List<String> m = flagMismatches(c, tris);
      System.out.println(
          (latLon ? "[LatLon] " : "[XY] ")
              + c.label
              + ": "
              + tris.size()
              + " triangles, flag mismatches="
              + m);
      for (String s : m) bad.add(c.label + ": " + s);
    }
    return bad;
  }

  public void testXYShapeContainsTwoHolesOnOuterEdge() throws Exception {
    List<String> bad = runContains(twoHoleCases(), false);
    assertTrue(bad.size() + " of " + twoHoleCases().size() + " wrong: " + bad, bad.isEmpty());
  }

  public void testLatLonShapeContainsTwoHolesOnOuterEdge() throws Exception {
    List<String> bad = runContains(twoHoleCases(), true);
    assertTrue(bad.size() + " of " + twoHoleCases().size() + " wrong: " + bad, bad.isEmpty());
  }

  public void testXYTessellatorEdgeFlagsTwoHolesOnOuterEdge() {
    List<String> bad = runFlags(twoHoleCases(), false);
    assertTrue(bad.size() + " wrong flags: " + bad, bad.isEmpty());
  }

  public void testLatLonTessellatorEdgeFlagsTwoHolesOnOuterEdge() {
    List<String> bad = runFlags(twoHoleCases(), true);
    assertTrue(bad.size() + " wrong flags: " + bad, bad.isEmpty());
  }

  /** Controls: one hole only (either one) must be handled correctly. Expected to pass. */
  public void testControlSingleHole() throws Exception {
    List<String> bad = new ArrayList<>();
    bad.addAll(runContains(singleHoleControls(), false));
    bad.addAll(runContains(singleHoleControls(), true));
    bad.addAll(runFlags(singleHoleControls(), false));
    bad.addAll(runFlags(singleHoleControls(), true));
    assertTrue("single-hole controls wrong: " + bad, bad.isEmpty());
  }
}
