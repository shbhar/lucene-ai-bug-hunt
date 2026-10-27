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
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * A FLOAT or DOUBLE index sort puts every NaN after +Infinity (Float.compare / Double.compare), at
 * flush time and at search time. A merge must keep that order, also for a NaN whose sign bit is
 * set, which is what {@code -x} gives at run time for {@code x = NaN}.
 */
public class TestIndexSortSignedNaNMerge extends LuceneTestCase {

  /** A NaN with the sign bit set, e.g. {@code Math.copySign(Float.NaN, -1f)}. */
  private static final float SIGNED_NAN_F = Float.intBitsToFloat(0xffc00000);

  private static final double SIGNED_NAN_D = Double.longBitsToDouble(0xfff8000000000000L);

  /** Descending by Float.compare / Double.compare: every NaN sorts after +Infinity, so first. */
  private static final List<Double> EXPECTED = List.of(Double.NaN, 3.0, 2.0, 1.0, 0.5);

  /** Descending: segment 1 = {3, 1, NaN}, segment 2 = {2, 0.5}, then forceMerge(1). */
  private static Directory buildIndex(SortField.Type type, boolean signedNaN) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setIndexSort(new Sort(new SortField("f", type, true)));
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      double[][] segments = {{3, 1, Double.NaN}, {2, 0.5}};
      for (double[] segment : segments) {
        for (double v : segment) {
          Document doc = new Document();
          doc.add(new StringField("isNaN", Double.isNaN(v) ? "yes" : "no", Field.Store.NO));
          if (type == SortField.Type.FLOAT) {
            float f = Double.isNaN(v) ? (signedNaN ? SIGNED_NAN_F : Float.NaN) : (float) v;
            doc.add(new FloatDocValuesField("f", f));
          } else {
            double d = Double.isNaN(v) ? (signedNaN ? SIGNED_NAN_D : Double.NaN) : v;
            doc.add(new DoubleDocValuesField("f", d));
          }
          w.addDocument(doc);
        }
        w.flush();
      }
      w.forceMerge(1);
    }
    return dir;
  }

  /** The values of the merged segment, in doc ID order. */
  private static List<Double> mergedOrder(Directory dir, SortField.Type type) throws IOException {
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(1, reader.leaves().size());
      LeafReader leaf = reader.leaves().get(0).reader();
      NumericDocValues dv = leaf.getNumericDocValues("f");
      List<Double> order = new ArrayList<>();
      for (int doc = 0; doc < leaf.maxDoc(); doc++) {
        assertTrue(dv.advanceExact(doc));
        long bits = dv.longValue();
        order.add(
            type == SortField.Type.FLOAT
                ? (double) Float.intBitsToFloat((int) bits)
                : Double.longBitsToDouble(bits));
      }
      return order;
    }
  }

  public void testFloatMergedOrder() throws IOException {
    try (Directory dir = buildIndex(SortField.Type.FLOAT, true)) {
      assertEquals(EXPECTED, mergedOrder(dir, SortField.Type.FLOAT));
    }
  }

  public void testDoubleMergedOrder() throws IOException {
    try (Directory dir = buildIndex(SortField.Type.DOUBLE, true)) {
      assertEquals(EXPECTED, mergedOrder(dir, SortField.Type.DOUBLE));
    }
  }

  /** Lucene's own index sort check (CheckIndex at level 2 or higher runs it). */
  public void testFloatCheckIndexSort() throws IOException {
    try (Directory dir = buildIndex(SortField.Type.FLOAT, true);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      CodecReader leaf = (CodecReader) reader.leaves().get(0).reader();
      Sort indexSort = new Sort(new SortField("f", SortField.Type.FLOAT, true));
      Throwable error = CheckIndex.testSort(leaf, indexSort, null, false).error;
      assertNull(error == null ? null : error.getMessage(), error);
    }
  }

  /** Top 1 by the index sort of a query that does not match the NaN document: 3.0. */
  public void testFloatTopHitWithoutNaNDoc() throws IOException {
    try (Directory dir = buildIndex(SortField.Type.FLOAT, true);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      Sort indexSort = new Sort(new SortField("f", SortField.Type.FLOAT, true));
      TopFieldDocs top = searcher.search(new TermQuery(new Term("isNaN", "no")), 1, indexSort);
      assertEquals(3.0f, ((FieldDoc) top.scoreDocs[0]).fields[0]);
    }
  }

  /** Control: the canonical NaN (sign bit clear) is merged correctly. */
  public void testFloatCanonicalNaNControl() throws IOException {
    try (Directory dir = buildIndex(SortField.Type.FLOAT, false)) {
      assertEquals(EXPECTED, mergedOrder(dir, SortField.Type.FLOAT));
    }
  }
}
