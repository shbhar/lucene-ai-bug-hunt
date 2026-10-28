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
import org.apache.lucene.geo.Line;
import org.apache.lucene.geo.XYLine;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: a WITHIN query whose query geometry is a line.
 *
 * <p>LatLonShape rejects it with IllegalArgumentException. XYShape accepts it. Uses only public
 * API (XYShape / LatLonShape factories, XYLine, Line).
 */
public class TestVerifyXYWithinLine extends LuceneTestCase {

  private static final String F = "shape";

  // L-shaped polyline query: (0,0) -> (0,10) -> (10,10)
  private static XYLine queryXYLine() {
    return new XYLine(new float[] {0f, 0f, 10f}, new float[] {0f, 10f, 10f});
  }

  /** Control: the geo twin rejects WITHIN + line. Expected to pass on every version. */
  public void testControlLatLonRejectsWithinLine() {
    Line line = new Line(new double[] {0, 0, 1}, new double[] {0, 1, 1});
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class,
            () -> LatLonShape.newLineQuery(F, QueryRelation.WITHIN, line));
    assertTrue(e.getMessage(), e.getMessage().contains("WITHIN"));
  }

  /** Claim A: XYShape should reject WITHIN + line like its twin. */
  public void testXYRejectsWithinLine() {
    XYLine line = queryXYLine();
    expectThrows(
        IllegalArgumentException.class,
        () -> XYShape.newLineQuery(F, QueryRelation.WITHIN, line));
  }

  /**
   * Claim B: if WITHIN + line is accepted, its answer must be the set of indexed shapes lying
   * entirely on the query line. Points on the line are returned; lines lying on it are not.
   */
  public void testXYWithinLineResults() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    // id -> shape; "in:" = lies entirely on the query line, "out:" = does not
    addPoint(w, "in:pt(0,5)", 0f, 5f);
    addPoint(w, "in:pt(5,10)", 5f, 10f);
    addPoint(w, "in:pt(0,10)corner", 0f, 10f);
    addPoint(w, "out:pt(5,5)", 5f, 5f);
    addLine(w, "in:line=query", new float[] {0f, 0f, 10f}, new float[] {0f, 10f, 10f});
    addLine(w, "in:seg(0,2)-(0,8)", new float[] {0f, 0f}, new float[] {2f, 8f});
    addLine(w, "in:seg(3,10)-(7,10)", new float[] {3f, 7f}, new float[] {10f, 10f});
    addLine(w, "out:diag(0,0)-(10,10)", new float[] {0f, 10f}, new float[] {0f, 10f});
    IndexReader r = w.getReader();
    w.close();
    IndexSearcher s = newSearcher(r);

    // Sanity control: INTERSECTS works and touches everything except the off-line point.
    Set<String> intersects = ids(s, XYShape.newLineQuery(F, QueryRelation.INTERSECTS, queryXYLine()));
    Set<String> expectedIntersects = new TreeSet<>();
    for (String id : allIds()) {
      if (id.equals("out:pt(5,5)") == false) {
        expectedIntersects.add(id);
      }
    }
    assertEquals("INTERSECTS control", expectedIntersects, intersects);

    Query within;
    try {
      within = XYShape.newLineQuery(F, QueryRelation.WITHIN, queryXYLine());
    } catch (IllegalArgumentException e) {
      // rejected like the LatLon twin: nothing to compare
      r.close();
      dir.close();
      return;
    }
    Set<String> got = ids(s, within);
    Set<String> expected = new TreeSet<>();
    for (String id : allIds()) {
      if (id.startsWith("in:")) {
        expected.add(id);
      }
    }
    r.close();
    dir.close();
    assertEquals("WITHIN line accepted, result must be every shape lying on the line", expected, got);
  }

  private static String[] allIds() {
    return new String[] {
      "in:pt(0,5)",
      "in:pt(5,10)",
      "in:pt(0,10)corner",
      "out:pt(5,5)",
      "in:line=query",
      "in:seg(0,2)-(0,8)",
      "in:seg(3,10)-(7,10)",
      "out:diag(0,0)-(10,10)"
    };
  }

  private static Set<String> ids(IndexSearcher s, Query q) throws Exception {
    TopDocs td = s.search(q, 100);
    Set<String> out = new TreeSet<>();
    for (ScoreDoc sd : td.scoreDocs) {
      out.add(s.storedFields().document(sd.doc).get("id"));
    }
    return out;
  }

  private static void addPoint(RandomIndexWriter w, String id, float x, float y) throws Exception {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    for (Field f : XYShape.createIndexableFields(F, x, y)) {
      d.add(f);
    }
    w.addDocument(d);
  }

  private static void addLine(RandomIndexWriter w, String id, float[] xs, float[] ys)
      throws Exception {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    for (Field f : XYShape.createIndexableFields(F, new XYLine(xs, ys))) {
      d.add(f);
    }
    w.addDocument(d);
  }
}
