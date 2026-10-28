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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * LatLonShape rejects a WITHIN query with a line geometry ("does not support WITHIN queries with
 * line geometries"), and so did the original XYShape line query. XYShape now accepts it, but
 * Line2D.containsLine/containsTriangle always return false, so the query silently returns only
 * points that lie on the line and never a line, not even one identical to the query line.
 */
public class TestXYShapeWithinLineQuery extends LuceneTestCase {

  public void testLatLonTwinRejectsWithinLine() {
    Line line = new Line(new double[] {0, 0}, new double[] {0, 10});
    expectThrows(
        IllegalArgumentException.class,
        () -> LatLonShape.newLineQuery("shape", QueryRelation.WITHIN, line));
  }

  public void testXYWithinLineRejectedOrComplete() throws Exception {
    XYLine queryLine = new XYLine(new float[] {0, 10}, new float[] {0, 0});
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
      // doc 0: the query line itself; doc 1: a point on it; doc 2: a segment of it
      addShape(w, "same line", XYShape.createIndexableFields("shape", queryLine));
      addShape(w, "point on line", XYShape.createIndexableFields("shape", 5f, 0f));
      addShape(
          w,
          "sub-segment",
          XYShape.createIndexableFields("shape", new XYLine(new float[] {2, 4}, new float[] {0, 0})));
      w.forceMerge(1);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        w.close();
        IndexSearcher searcher = new IndexSearcher(reader);
        final Query query;
        try {
          query = XYShape.newLineQuery("shape", QueryRelation.WITHIN, queryLine);
        } catch (IllegalArgumentException e) {
          return; // rejected like its LatLonShape twin: fine
        }
        TopDocs hits = searcher.search(query, 10);
        Set<String> found = new TreeSet<>();
        for (ScoreDoc sd : hits.scoreDocs) {
          found.add(searcher.storedFields().document(sd.doc).get("id"));
        }
        // the query accepted WITHIN and matched the point on the line, so every shape that lies
        // on the line must match too
        assertEquals(Set.of("point on line", "same line", "sub-segment"), found);
      }
    }
  }

  private static void addShape(IndexWriter w, String id, Field[] fields) throws Exception {
    Document doc = new Document();
    for (Field f : fields) {
      doc.add(f);
    }
    doc.add(new StoredField("id", id));
    w.addDocument(doc);
  }
}
