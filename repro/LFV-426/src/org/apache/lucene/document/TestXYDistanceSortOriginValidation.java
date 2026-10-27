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

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * XYDocValuesField.newDistanceSort documents "@throws IllegalArgumentException if field is null
 * or location has invalid coordinates", and every other XY entry point (XYPoint, XYCircle,
 * XYRectangle, XYDocValuesField, XYPointField) rejects non-finite coordinates. Its LatLon twin
 * LatLonDocValuesField.newDistanceSort validates the origin, but XYPointSortField does not.
 */
public class TestXYDistanceSortOriginValidation extends LuceneTestCase {

  public void testLatLonTwinRejectsInvalidOrigin() {
    expectThrows(
        IllegalArgumentException.class,
        () -> LatLonDocValuesField.newDistanceSort("p", Double.NaN, 0));
  }

  public void testNonFiniteOriginRejected() {
    // the same coordinates are rejected when building an XY point or circle
    expectThrows(IllegalArgumentException.class, () -> new XYDocValuesField("p", Float.NaN, 0f));
    for (float[] origin :
        new float[][] {
          {Float.NaN, 0f}, {0f, Float.NaN}, {Float.POSITIVE_INFINITY, 0f}, {0f, Float.NEGATIVE_INFINITY}
        }) {
      expectThrows(
          IllegalArgumentException.class,
          () -> XYDocValuesField.newDistanceSort("p", origin[0], origin[1]));
    }
  }

  /** What the accepted NaN origin does: the document without a value no longer sorts last. */
  public void testMissingValueSortsLastWithAcceptedOrigin() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
      Document d0 = new Document();
      d0.add(new XYDocValuesField("p", 1f, 1f));
      w.addDocument(d0);
      w.addDocument(new Document()); // doc 1: no location
      w.forceMerge(1);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        w.close();
        IndexSearcher searcher = new IndexSearcher(reader);
        Sort sort;
        try {
          sort = new Sort(XYDocValuesField.newDistanceSort("p", Float.NaN, 0f));
        } catch (IllegalArgumentException e) {
          return; // rejected as documented
        }
        TopDocs td = searcher.search(MatchAllDocsQuery.INSTANCE, 2, sort);
        // javadoc: "If a document is missing the field, then by default it is treated as having
        // Double#POSITIVE_INFINITY distance (missing values sort last)."
        assertEquals(
            "missing document must sort last, sort values "
                + ((FieldDoc) td.scoreDocs[0]).fields[0]
                + ", "
                + ((FieldDoc) td.scoreDocs[1]).fields[0],
            1,
            td.scoreDocs[1].doc);
      }
    }
  }
}
