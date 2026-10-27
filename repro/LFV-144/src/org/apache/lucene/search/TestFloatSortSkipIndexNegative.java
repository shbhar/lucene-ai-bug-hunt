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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Sorting on a FLOAT or DOUBLE field whose doc values have a skip index (and no points) returns
 * wrong hits when values are negative.
 *
 * <p>The field "skip" holds the raw IEEE bits, as FloatDocValuesField / DoubleDocValuesField do,
 * plus a doc-values skip index. This is the layout of
 * TestSortOptimization#testFloatSortOptimizationSkpperIndex, only with negative values. The field
 * "plain" holds the same values without a skip index and is the control.
 *
 * <p>Document i (0 <= i < 10000) has the value -i.
 */
public class TestFloatSortSkipIndexNegative extends LuceneTestCase {

  private static final int NUM_DOCS = 10_000;
  private static final int TOP_N = 3;

  public void testFloatAscending() throws IOException {
    // ascending: the best values are the most negative ones
    check(SortField.Type.FLOAT, false, -1, NUM_DOCS - 1, NUM_DOCS - 2, NUM_DOCS - 3);
  }

  public void testDoubleAscending() throws IOException {
    check(SortField.Type.DOUBLE, false, -1, NUM_DOCS - 1, NUM_DOCS - 2, NUM_DOCS - 3);
  }

  public void testFloatAscendingSearchAfter() throws IOException {
    // the page after the hit with value -9997
    check(SortField.Type.FLOAT, false, 9997, 9996, 9995, 9994);
  }

  public void testFloatDescendingSearchAfter() throws IOException {
    // descending: the page after the hit with value -9000
    check(SortField.Type.FLOAT, true, 9000, 9001, 9002, 9003);
  }

  public void testDoubleDescendingSearchAfter() throws IOException {
    check(SortField.Type.DOUBLE, true, 9000, 9001, 9002, 9003);
  }

  /**
   * Only -0.0 values: the descending sort with a doc-id tie-break (reverse doc order) must return
   * the three largest doc ids, as it does on the control field without a skip index.
   */
  public void testDoubleNegativeZeroDescendingWithTieBreak() throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < NUM_DOCS; i++) {
          Document doc = new Document();
          doc.add(new StringField("all", "x", Field.Store.NO));
          doc.add(NumericDocValuesField.indexedField("skip", Double.doubleToRawLongBits(-0.0d)));
          doc.add(new DoubleDocValuesField("plain", -0.0d));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        Query query = new TermQuery(new Term("all", "x"));
        int[] expected = {NUM_DOCS - 1, NUM_DOCS - 2, NUM_DOCS - 3};
        for (String field : new String[] {"plain", "skip"}) {
          Sort sort =
              new Sort(
                  new SortField(field, SortField.Type.DOUBLE, true),
                  new SortField(null, SortField.Type.DOC, true));
          TopFieldDocs td = searcher.search(query, TOP_N, sort);
          int[] docs = new int[td.scoreDocs.length];
          for (int i = 0; i < docs.length; i++) {
            docs[i] = td.scoreDocs[i].doc;
          }
          assertArrayEquals(
              "field " + field + ": got " + Arrays.toString(docs), expected, docs);
        }
      }
    }
  }

  /**
   * @param afterDoc -1 for a first page, otherwise the doc (and value -afterDoc) to page after
   * @param expectedDocs the expected hits; hit i has the value -expectedDocs[i]
   */
  private void check(SortField.Type type, boolean reverse, int afterDoc, int... expectedDocs)
      throws IOException {
    boolean isFloat = type == SortField.Type.FLOAT;
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < NUM_DOCS; i++) {
          Document doc = new Document();
          doc.add(new StringField("all", "x", Field.Store.NO));
          if (isFloat) {
            float v = -(float) i;
            doc.add(NumericDocValuesField.indexedField("skip", Float.floatToRawIntBits(v)));
            doc.add(new FloatDocValuesField("plain", v));
          } else {
            double v = -(double) i;
            doc.add(NumericDocValuesField.indexedField("skip", Double.doubleToRawLongBits(v)));
            doc.add(new DoubleDocValuesField("plain", v));
          }
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        // no query cache: a cached TermQuery is scored by another bulk scorer that does not use
        // the competitive iterator here, which would make the outcome depend on the test seed
        searcher.setQueryCache(null);
        Query query = new TermQuery(new Term("all", "x"));
        Object[] expected = new Object[expectedDocs.length];
        for (int i = 0; i < expected.length; i++) {
          expected[i] = isFloat ? (Object) (-(float) expectedDocs[i]) : (Object) (-(double) expectedDocs[i]);
        }
        FieldDoc after = null;
        if (afterDoc >= 0) {
          Object afterValue = isFloat ? (Object) (-(float) afterDoc) : (Object) (-(double) afterDoc);
          after = new FieldDoc(afterDoc, Float.NaN, new Object[] {afterValue});
        }
        Object[] plain = values(search(searcher, query, "plain", type, reverse, after));
        Object[] skipped = values(search(searcher, query, "skip", type, reverse, after));
        assertArrayEquals("control field without a skip index", expected, plain);
        assertArrayEquals(
            "field with a skip index: got " + Arrays.toString(skipped), expected, skipped);
      }
    }
  }

  private static TopFieldDocs search(
      IndexSearcher searcher,
      Query query,
      String field,
      SortField.Type type,
      boolean reverse,
      FieldDoc after)
      throws IOException {
    Sort sort = new Sort(new SortField(field, type, reverse));
    return after == null
        ? searcher.search(query, TOP_N, sort)
        : (TopFieldDocs) searcher.searchAfter(after, query, TOP_N, sort);
  }

  private static Object[] values(TopFieldDocs td) {
    Object[] out = new Object[td.scoreDocs.length];
    for (int i = 0; i < out.length; i++) {
      out[i] = ((FieldDoc) td.scoreDocs[i]).fields[0];
    }
    return out;
  }
}
