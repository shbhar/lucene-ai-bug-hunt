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
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.XYLine;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * LatLonShape rejects a WITHIN query with a line geometry, and XYShape did too up to 8.4.1. XYShape
 * should reject it as well instead of answering it partially.
 */
public class TestXYShapeWithinLine extends LuceneTestCase {

  private static final XYLine LINE = new XYLine(new float[] {0, 10}, new float[] {0, 0});

  /** Control: the lat/lon twin rejects WITHIN with a line, alone or in a geometry array. */
  public void testLatLonShapeRejectsWithinLine() {
    Line line = new Line(new double[] {0, 0}, new double[] {0, 10});
    Polygon polygon =
        new Polygon(new double[] {20, 20, 21, 21, 20}, new double[] {20, 21, 21, 20, 20});
    expectThrows(
        IllegalArgumentException.class,
        () -> LatLonShape.newLineQuery("shape", QueryRelation.WITHIN, line));
    expectThrows(
        IllegalArgumentException.class,
        () -> LatLonShape.newGeometryQuery("shape", QueryRelation.WITHIN, polygon, line));
  }

  /** XYShape accepts the same query and then matches only the indexed point on the line. */
  public void testXYShapeRejectsWithinLine() throws Exception {
    try (Directory dir = newDirectory()) {
      RandomIndexWriter w = new RandomIndexWriter(random(), dir);
      add(w, "the same line", XYShape.createIndexableFields("shape", LINE));
      add(
          w,
          "segment (2,0)-(4,0)",
          XYShape.createIndexableFields(
              "shape", new XYLine(new float[] {2, 4}, new float[] {0, 0})));
      add(w, "point (5,0)", XYShape.createIndexableFields("shape", 5f, 0f));
      try (IndexReader reader = w.getReader()) {
        w.close();
        IndexSearcher searcher = newSearcher(reader);
        final Query query;
        try {
          query = XYShape.newLineQuery("shape", QueryRelation.WITHIN, LINE);
        } catch (IllegalArgumentException e) {
          assertTrue(e.getMessage(), e.getMessage().contains("WITHIN"));
          return; // rejected, as LatLonShape does
        }
        fail(
            "XYShape.newLineQuery(WITHIN, line (0,0)-(10,0)) was accepted and matched "
                + ids(searcher, query)
                + " of the 3 indexed shapes");
      }
    }
  }

  /** The same for a geometry array that contains a line. */
  public void testXYShapeRejectsWithinGeometryArrayWithLine() {
    XYPolygon polygon =
        new XYPolygon(new float[] {20, 21, 21, 20, 20}, new float[] {20, 20, 21, 21, 20});
    expectThrows(
        IllegalArgumentException.class,
        () -> XYShape.newGeometryQuery("shape", QueryRelation.WITHIN, polygon, LINE));
  }

  private static void add(RandomIndexWriter w, String id, Field[] fields) throws Exception {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    for (Field f : fields) {
      doc.add(f);
    }
    w.addDocument(doc);
  }

  private static Set<String> ids(IndexSearcher searcher, Query query) throws Exception {
    Set<String> ids = new TreeSet<>();
    for (ScoreDoc sd : searcher.search(query, 10).scoreDocs) {
      ids.add(searcher.storedFields().document(sd.doc).get("id"));
    }
    return ids;
  }
}
