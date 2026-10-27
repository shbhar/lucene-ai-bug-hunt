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
package org.apache.lucene.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * An index sorted by a FLOAT (or DOUBLE) SortField orders a NaN last (Float.compare / Double.compare,
 * as FloatComparator and the flush-time IndexSorter.FloatSorter do). A NaN whose sign bit is set
 * (raw bits 0xffc00000, kept as-is by FloatDocValuesField, which stores Float.floatToRawIntBits) is
 * placed elsewhere when segments are merged, because the merge-time comparable values are
 * NumericUtils.sortableFloatBits of the raw bits. The merged segment then violates its own index
 * sort, and a search sorted by the index sort terminates early on the wrong documents.
 */
public class TestIndexSortMergeSignedNaN extends LuceneTestCase {

  private static final float NEG_NAN_F = Float.intBitsToFloat(0xffc00000);
  private static final double NEG_NAN_D = Double.longBitsToDouble(0xfff8000000000000L);

  public void testFloatDescending() throws IOException {
    check(SortField.Type.FLOAT, true);
  }

  public void testDoubleDescending() throws IOException {
    check(SortField.Type.DOUBLE, true);
  }

  /** Only the search: top hit of a query that does not match the NaN document. */
  public void testFloatDescendingSearchWithoutNaNDoc() throws IOException {
    checkSearchOnly(SortField.Type.FLOAT);
  }

  public void testFloatAscending() throws IOException {
    check(SortField.Type.FLOAT, false);
  }

  private static void addDoc(IndexWriter w, SortField.Type type, double v) throws IOException {
    Document doc = new Document();
    doc.add(new StringField("isNaN", Double.isNaN(v) ? "yes" : "no", Field.Store.NO));
    if (type == SortField.Type.FLOAT) {
      doc.add(new FloatDocValuesField("f", Double.isNaN(v) ? NEG_NAN_F : (float) v));
    } else {
      doc.add(new DoubleDocValuesField("f", Double.isNaN(v) ? NEG_NAN_D : v));
    }
    w.addDocument(doc);
  }

  private void checkSearchOnly(SortField.Type type) throws IOException {
    SortField sortField = new SortField("f", type, true);
    Sort indexSort = new Sort(sortField);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setIndexSort(indexSort))) {
        addDoc(w, type, 3.0);
        addDoc(w, type, 1.0);
        addDoc(w, type, Double.NaN);
        w.flush();
        addDoc(w, type, 2.0);
        addDoc(w, type, 0.5);
        w.flush();
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopFieldDocs top = searcher.search(new TermQuery(new Term("isNaN", "no")), 1, indexSort);
        // the largest value among the non-NaN documents is 3.0
        assertEquals(3.0, ((Number) ((FieldDoc) top.scoreDocs[0]).fields[0]).doubleValue(), 0.0);
      }
    }
  }

  private void check(SortField.Type type, boolean reverse) throws IOException {
    SortField sortField = new SortField("f", type, reverse);
    Sort indexSort = new Sort(sortField);
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setIndexSort(indexSort);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        // descending: segment 1 = {3, 1, NaN with the sign bit set}, segment 2 = {2, 0.5}
        // ascending:  segment 1 = {1, NaN with the sign bit set},    segment 2 = {0.5, 2, 3}
        double[][] segments =
            reverse
                ? new double[][] {{3.0, 1.0, Double.NaN}, {2.0, 0.5}}
                : new double[][] {{1.0, Double.NaN}, {0.5, 2.0, 3.0}};
        for (double[] segment : segments) {
          for (double v : segment) {
            addDoc(w, type, v);
          }
          w.flush();
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        LeafReader leaf = reader.leaves().get(0).reader();

        // 1) the merged segment must be sorted by its index sort (Float.compare / Double.compare)
        NumericDocValues dv = leaf.getNumericDocValues("f");
        List<Double> order = new ArrayList<>();
        for (int doc = 0; doc < leaf.maxDoc(); doc++) {
          assertTrue(dv.advanceExact(doc));
          order.add(
              type == SortField.Type.FLOAT
                  ? (double) Float.intBitsToFloat((int) dv.longValue())
                  : Double.longBitsToDouble(dv.longValue()));
        }
        for (int i = 1; i < order.size(); i++) {
          int cmp = Double.compare(order.get(i - 1), order.get(i));
          assertTrue(
              "merged segment is not sorted by " + indexSort + ": " + order,
              reverse ? cmp >= 0 : cmp <= 0);
        }

        // 2) a search sorted by the index sort returns the top hit of the sort order
        IndexSearcher searcher = new IndexSearcher(reader);
        TopFieldDocs top = searcher.search(new MatchAllDocsQuery(), 1, indexSort);
        TopFieldDocs ref =
            searcher.search(new MatchAllDocsQuery(), 1, new Sort(sortField, SortField.FIELD_DOC));
        assertEquals(
            "top hit", ((FieldDoc) ref.scoreDocs[0]).fields[0], ((FieldDoc) top.scoreDocs[0]).fields[0]);

        // 3) the same for a query that does not match the NaN document
        TermQuery noNaN = new TermQuery(new Term("isNaN", "no"));
        top = searcher.search(noNaN, 1, indexSort);
        ref = searcher.search(noNaN, 1, new Sort(sortField, SortField.FIELD_DOC));
        assertEquals(
            "top hit without the NaN document",
            ((FieldDoc) ref.scoreDocs[0]).fields[0],
            ((FieldDoc) top.scoreDocs[0]).fields[0]);
      }
    }
  }
}
