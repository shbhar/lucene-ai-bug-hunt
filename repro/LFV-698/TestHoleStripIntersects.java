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

import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.Component2D;
import org.apache.lucene.geo.LatLonGeometry;
import org.apache.lucene.geo.Line;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.XYGeometry;
import org.apache.lucene.geo.XYLine;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Query polygon: the square [-10,10]x[-10,10] (x = lon, y = lat) with two disjoint holes
 * [-8,-2]x[-2,2] and [2,8]x[-2,2]. The vertical strip -2 < x < 2 between the holes belongs to the
 * polygon. A segment or polygon whose vertices all lie strictly inside the holes but which crosses
 * that strip intersects the polygon.
 *
 * <p>Uses only public API (Component2D is a public, internal-marked interface).
 */
public class TestHoleStripIntersects extends LuceneTestCase {

  // shell clockwise, holes counter-clockwise (x = lon, y = lat), as the Polygon javadoc asks
  private static final double[] SHELL_X = {-10, -10, 10, 10, -10};
  private static final double[] SHELL_Y = {-10, 10, 10, -10, -10};
  private static final double[] HOLE1_X = {-8, -2, -2, -8, -8};
  private static final double[] HOLE2_X = {2, 8, 8, 2, 2};
  private static final double[] HOLE_Y = {-2, -2, 2, 2, -2};

  // documents: {ax, ay, bx, by}; doc 1 is a polygon, the others are lines
  private static final double[][] LINES = {
    {-5, 0, 5, 0}, // doc 0: from hole 1 to hole 2 across the strip -> INTERSECTS
    null, // doc 1: polygon box [-5,5]x[-1,1], all four vertices in holes -> INTERSECTS
    {-1, 0, 1, 0}, // doc 2: entirely in the strip (control) -> INTERSECTS
    {-7, 0, -3, 0}, // doc 3: entirely inside hole 1 (control) -> DISJOINT
    {-5, 20, 5, 20}, // doc 4: outside the shell (control) -> DISJOINT
  };
  private static final double[] BOX_X = {-5, -5, 5, 5, -5}; // clockwise box
  private static final double[] BOX_Y = {-1, 1, 1, -1, -1};

  private static final Set<Integer> EXPECTED_INTERSECTS = Set.of(0, 1, 2);
  private static final Set<Integer> EXPECTED_DISJOINT = Set.of(3, 4);

  private static double[] f(double[] a) {
    return a.clone();
  }

  private static float[] toFloat(double[] a) {
    float[] r = new float[a.length];
    for (int i = 0; i < a.length; i++) {
      r[i] = (float) a[i];
    }
    return r;
  }

  private static Polygon latLonQueryPolygon() {
    Polygon h1 = new Polygon(f(HOLE_Y), f(HOLE1_X));
    Polygon h2 = new Polygon(f(HOLE_Y), f(HOLE2_X));
    return new Polygon(f(SHELL_Y), f(SHELL_X), h1, h2);
  }

  private static XYPolygon xyQueryPolygon() {
    XYPolygon h1 = new XYPolygon(toFloat(HOLE1_X), toFloat(HOLE_Y));
    XYPolygon h2 = new XYPolygon(toFloat(HOLE2_X), toFloat(HOLE_Y));
    return new XYPolygon(toFloat(SHELL_X), toFloat(SHELL_Y), h1, h2);
  }

  /** Independent oracle: does any sampled point of segment a-b lie in the component? */
  private static boolean sampledIntersects(
      Component2D c, double ax, double ay, double bx, double by) {
    int n = 10_000;
    for (int i = 0; i <= n; i++) {
      double t = (double) i / n;
      if (c.contains(ax + t * (bx - ax), ay + t * (by - ay))) {
        return true;
      }
    }
    return false;
  }

  private static void checkComponent(Component2D c, String kind) {
    // sanity: the strip point (0,0) is inside, hole centres are not
    assertTrue(kind + ": (0,0) must be inside the polygon", c.contains(0, 0));
    assertFalse(kind + ": (-5,0) is in hole 1", c.contains(-5, 0));
    assertFalse(kind + ": (5,0) is in hole 2", c.contains(5, 0));
    StringBuilder errors = new StringBuilder();
    for (int d = 0; d < LINES.length; d++) {
      double[] l = LINES[d];
      if (l == null) {
        continue;
      }
      boolean oracle = sampledIntersects(c, l[0], l[1], l[2], l[3]);
      boolean actual = c.intersectsLine(l[0], l[1], l[2], l[3]);
      if (oracle != actual) {
        errors.append(
            String.format(
                java.util.Locale.ROOT,
                "%s doc %d intersectsLine(%s,%s -> %s,%s)=%s but a sampled point of it is %s the polygon%n",
                kind,
                d,
                l[0],
                l[1],
                l[2],
                l[3],
                actual,
                oracle ? "inside" : "never inside"));
      }
    }
    // triangle with all three vertices inside holes whose interior covers (0,0) and (1,-0.5)
    boolean tri = c.intersectsTriangle(-5, -1, 5, -1, 5, 1);
    if (tri == false) {
      errors.append(
          kind
              + " intersectsTriangle((-5,-1),(5,-1),(5,1))=false although its interior point (1,-0.5)"
              + " is contained: "
              + c.contains(1, -0.5)
              + "\n");
    }
    assertTrue(errors.toString(), errors.length() == 0);
  }

  public void testComponentLatLon() {
    checkComponent(LatLonGeometry.create(latLonQueryPolygon()), "LatLon Component2D");
  }

  public void testComponentXY() {
    checkComponent(XYGeometry.create(xyQueryPolygon()), "XY Component2D");
  }

  private static Set<Integer> run(IndexSearcher s, Query q) throws Exception {
    TopDocs td = s.search(q, 100);
    StoredFields sf = s.storedFields();
    Set<Integer> ids = new TreeSet<>();
    for (ScoreDoc sd : td.scoreDocs) {
      ids.add(Integer.parseInt(sf.document(sd.doc).get("id")));
    }
    return ids;
  }

  public void testLatLonShapeEndToEnd() throws Exception {
    try (Directory dir = newDirectory()) {
      RandomIndexWriter w = new RandomIndexWriter(random(), dir);
      for (int d = 0; d < LINES.length; d++) {
        Document doc = new Document();
        doc.add(new StoredField("id", Integer.toString(d)));
        Field[] fields;
        if (LINES[d] == null) {
          fields = LatLonShape.createIndexableFields("s", new Polygon(f(BOX_Y), f(BOX_X)));
        } else {
          double[] l = LINES[d];
          fields =
              LatLonShape.createIndexableFields(
                  "s", new Line(new double[] {l[1], l[3]}, new double[] {l[0], l[2]}));
        }
        for (Field fl : fields) {
          doc.add(fl);
        }
        w.addDocument(doc);
      }
      try (IndexReader r = w.getReader()) {
        w.close();
        IndexSearcher s = newSearcher(r);
        Polygon q = latLonQueryPolygon();
        Set<Integer> inter = run(s, LatLonShape.newPolygonQuery("s", QueryRelation.INTERSECTS, q));
        Set<Integer> disj = run(s, LatLonShape.newPolygonQuery("s", QueryRelation.DISJOINT, q));
        assertEquals(
            "LatLonShape INTERSECTS (DISJOINT returned " + disj + ")", EXPECTED_INTERSECTS, inter);
        assertEquals("LatLonShape DISJOINT", EXPECTED_DISJOINT, disj);
      }
    }
  }

  public void testXYShapeEndToEnd() throws Exception {
    try (Directory dir = newDirectory()) {
      RandomIndexWriter w = new RandomIndexWriter(random(), dir);
      for (int d = 0; d < LINES.length; d++) {
        Document doc = new Document();
        doc.add(new StoredField("id", Integer.toString(d)));
        Field[] fields;
        if (LINES[d] == null) {
          fields = XYShape.createIndexableFields("s", new XYPolygon(toFloat(BOX_X), toFloat(BOX_Y)));
        } else {
          double[] l = LINES[d];
          fields =
              XYShape.createIndexableFields(
                  "s",
                  new XYLine(
                      new float[] {(float) l[0], (float) l[2]},
                      new float[] {(float) l[1], (float) l[3]}));
        }
        for (Field fl : fields) {
          doc.add(fl);
        }
        w.addDocument(doc);
      }
      try (IndexReader r = w.getReader()) {
        w.close();
        IndexSearcher s = newSearcher(r);
        XYPolygon q = xyQueryPolygon();
        Set<Integer> inter = run(s, XYShape.newPolygonQuery("s", QueryRelation.INTERSECTS, q));
        Set<Integer> disj = run(s, XYShape.newPolygonQuery("s", QueryRelation.DISJOINT, q));
        assertEquals(
            "XYShape INTERSECTS (DISJOINT returned " + disj + ")", EXPECTED_INTERSECTS, inter);
        assertEquals("XYShape DISJOINT", EXPECTED_DISJOINT, disj);
      }
    }
  }
}
