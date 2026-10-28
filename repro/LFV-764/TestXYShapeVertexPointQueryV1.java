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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.Component2D;
import org.apache.lucene.geo.GeoUtils;
import org.apache.lucene.geo.XYEncodingUtils;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A point query placed exactly on a vertex of an indexed XY polygon should match the polygon with
 * INTERSECTS (and not report it as DISJOINT): shapes are closed sets, and every other vertex and
 * every edge point of the same polygon does match.
 *
 * <p>Public API only (XYShape, XYPolygon, IndexSearcher). The diagnostics read the stored triangle
 * back with the public ShapeField.decodeTriangle.
 */
public class TestXYShapeVertexPointQueryV1 extends LuceneTestCase {

  private static final String FIELD = "shape";

  /** Every non-degenerate triangle with vertices on the lattice {-2..2}^2, one document each. */
  public void testEveryLatticeTriangleMatchesAtItsVertices() throws IOException {
    List<float[][]> triangles = new ArrayList<>();
    List<float[]> pts = new ArrayList<>();
    for (int x = -2; x <= 2; x++) {
      for (int y = -2; y <= 2; y++) {
        pts.add(new float[] {x, y});
      }
    }
    for (int i = 0; i < pts.size(); i++) {
      for (int j = i + 1; j < pts.size(); j++) {
        for (int k = j + 1; k < pts.size(); k++) {
          float[] a = pts.get(i), b = pts.get(j), c = pts.get(k);
          if (GeoUtils.orient(a[0], a[1], b[0], b[1], c[0], c[1]) != 0) {
            triangles.add(new float[][] {a, b, c});
          }
        }
      }
    }
    List<String> failures = checkVertices(triangles);
    assertEquals(
        "vertex point queries that miss their own polygon ("
            + triangles.size()
            + " triangles, "
            + 3 * triangles.size()
            + " vertices checked): "
            + failures,
        0,
        failures.size());
  }

  /**
   * A thin triangle with metre-like coordinates whose x range crosses 2^19 and y range crosses 2^22.
   * The control is the same triangle moved by the exact translation (+10000, +10000), which puts each
   * axis inside a single power-of-two range.
   */
  public void testMetreLikeTriangleAndTranslatedControl() throws IOException {
    float[][] tri = {{525611f, 4189943f}, {527937f, 4186582f}, {517373f, 4202559f}};
    float[][] control = new float[3][];
    for (int i = 0; i < 3; i++) {
      control[i] = new float[] {tri[i][0] + 10000f, tri[i][1] + 10000f};
    }
    List<float[][]> controlOnly = new ArrayList<>();
    controlOnly.add(control);
    assertEquals("control triangle", 0, checkVertices(controlOnly).size());

    List<float[][]> original = new ArrayList<>();
    original.add(tri);
    List<String> failures = checkVertices(original);
    assertEquals("metre-like triangle: " + failures, 0, failures.size());
  }

  /**
   * Component level: Component2D.pointInTriangle (public static on the public interface) for the
   * same triangle given counter-clockwise and clockwise.
   */
  public void testPointInTriangleAtEveryVertexBothWindings() {
    // (0,0) (1,0) (0,1) is counter-clockwise; (0,0) (0,1) (1,0) is the same triangle clockwise.
    double[][] ccw = {{0, 0}, {1, 0}, {0, 1}};
    double[][] cw = {{0, 0}, {0, 1}, {1, 0}};
    List<String> misses = new ArrayList<>();
    for (double[][] t : new double[][][] {ccw, cw}) {
      for (double[] p : t) {
        boolean in =
            Component2D.pointInTriangle(
                0, 1, 0, 1, p[0], p[1], t[0][0], t[0][1], t[1][0], t[1][1], t[2][0], t[2][1]);
        if (in == false) {
          misses.add(
              "vertex ("
                  + p[0]
                  + ","
                  + p[1]
                  + ") of "
                  + (t == ccw ? "CCW" : "CW")
                  + " triangle reported outside");
        }
      }
    }
    assertEquals(misses.toString(), 0, misses.size());
  }

  /** Indexes one polygon per triangle and returns a description of every vertex query mismatch. */
  private List<String> checkVertices(List<float[][]> triangles) throws IOException {
    List<String> failures = new ArrayList<>();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      List<String> stored = new ArrayList<>();
      for (int id = 0; id < triangles.size(); id++) {
        float[][] t = triangles.get(id);
        XYPolygon poly =
            new XYPolygon(
                new float[] {t[0][0], t[1][0], t[2][0], t[0][0]},
                new float[] {t[0][1], t[1][1], t[2][1], t[0][1]});
        Document doc = new Document();
        doc.add(new IntPoint("id", id));
        Field[] fields = XYShape.createIndexableFields(FIELD, poly, true);
        StringBuilder sb = new StringBuilder();
        for (Field f : fields) {
          doc.add(f);
          sb.append(describeStored(f.binaryValue()));
        }
        stored.add(sb.toString());
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        for (int id = 0; id < triangles.size(); id++) {
          float[][] t = triangles.get(id);
          for (float[] v : t) {
            int inter = count(s, id, QueryRelation.INTERSECTS, v);
            int disj = count(s, id, QueryRelation.DISJOINT, v);
            if (inter != 1 || disj != 0) {
              failures.add(
                  "polygon "
                      + str(t)
                      + " vertex ("
                      + v[0]
                      + ","
                      + v[1]
                      + "): INTERSECTS hits="
                      + inter
                      + " DISJOINT hits="
                      + disj
                      + " stored="
                      + stored.get(id));
            }
          }
        }
      }
    }
    return failures;
  }

  private static int count(IndexSearcher s, int id, QueryRelation rel, float[] v)
      throws IOException {
    Query q =
        new BooleanQuery.Builder()
            .add(IntPoint.newExactQuery("id", id), BooleanClause.Occur.FILTER)
            .add(XYShape.newPointQuery(FIELD, rel, v), BooleanClause.Occur.FILTER)
            .build();
    return s.count(q);
  }

  /** Decodes a stored triangle and reports its vertex order and its winding in float space. */
  private static String describeStored(BytesRef br) {
    byte[] bytes = new byte[br.length];
    System.arraycopy(br.bytes, br.offset, bytes, 0, br.length);
    ShapeField.DecodedTriangle d = new ShapeField.DecodedTriangle();
    ShapeField.decodeTriangle(bytes, d);
    double ax = XYEncodingUtils.decode(d.aX), ay = XYEncodingUtils.decode(d.aY);
    double bx = XYEncodingUtils.decode(d.bX), by = XYEncodingUtils.decode(d.bY);
    double cx = XYEncodingUtils.decode(d.cX), cy = XYEncodingUtils.decode(d.cY);
    int o = GeoUtils.orient(ax, ay, bx, by, cx, cy);
    return "[a=("
        + ax
        + ","
        + ay
        + ") b=("
        + bx
        + ","
        + by
        + ") c=("
        + cx
        + ","
        + cy
        + ") float-winding="
        + (o > 0 ? "CCW" : o < 0 ? "CW" : "flat")
        + "]";
  }

  private static String str(float[][] t) {
    return "(" + t[0][0] + "," + t[0][1] + ")(" + t[1][0] + "," + t[1][1] + ")(" + t[2][0] + ","
        + t[2][1] + ")";
  }
}
