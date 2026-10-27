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

import java.util.List;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatch;
import org.apache.lucene.document.column.LongColumn;
import org.apache.lucene.document.column.LongTupleCursor;
import org.apache.lucene.document.column.ObjectTupleCursor;
import org.apache.lucene.document.column.VectorColumn;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * After a document is rejected, IndexWriter#addBatch on the same field caches the rejected
 * document's frozen FieldType as "validated", so later addDocument calls with that type skip the
 * schema check. With vectors of a larger dimension this silently stores truncated vectors.
 *
 * <p>Only public API is used: IndexWriter.addDocument / addBatch, ColumnBatch / LongColumn /
 * VectorColumn subclasses, and stock field classes.
 */
public class TestAddBatchCachesRejectedFieldType extends LuceneTestCase {

  private static ColumnBatch oneDocBatch(Column column) {
    return new ColumnBatch() {
      @Override
      public int numDocs() {
        return 1;
      }

      @Override
      public Iterable<Column> columns() {
        return List.of(column);
      }
    };
  }

  private static VectorColumn<float[]> vectorColumn(String name, FieldType type, float[] vector) {
    return new VectorColumn<>(name, type, Column.Density.SPARSE) {
      @Override
      public ObjectTupleCursor<float[]> tuples() {
        return new ObjectTupleCursor<>() {
          int doc = -1;

          @Override
          public int nextDoc() {
            return ++doc == 0 ? 0 : DocIdSetIterator.NO_MORE_DOCS;
          }

          @Override
          public float[] value() {
            return vector;
          }
        };
      }
    };
  }

  private static LongColumn numericColumn(String name, long value) {
    return new LongColumn(name, NumericDocValuesField.TYPE, Column.Density.SPARSE) {
      @Override
      public LongTupleCursor tuples() {
        return new LongTupleCursor() {
          int doc = -1;

          @Override
          public int nextDoc() {
            return ++doc == 0 ? 0 : DocIdSetIterator.NO_MORE_DOCS;
          }

          @Override
          public long longValue() {
            return value;
          }
        };
      }
    };
  }

  /** The production default codec (the test framework otherwise randomizes the codec). */
  private static IndexWriterConfig newConfig() {
    return new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
  }

  public void testVectorOfWrongDimensionAcceptedAndTruncated() throws Exception {
    FieldType dim4 = KnnFloatVectorField.createFieldType(4, VectorSimilarityFunction.EUCLIDEAN);
    FieldType dim8 = KnnFloatVectorField.createFieldType(8, VectorSimilarityFunction.EUCLIDEAN);
    float[] eightDims = {1, 2, 3, 4, 5, 6, 7, 8};
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newConfig())) {
      w.addBatch(oneDocBatch(vectorColumn("v", dim4, new float[] {1, 1, 1, 1})));

      // Correctly rejected: "v" has 4 dimensions in this index.
      expectThrows(
          IllegalArgumentException.class,
          () -> w.addDocument(List.of(new KnnFloatVectorField("v", eightDims, dim8))));

      w.addBatch(oneDocBatch(vectorColumn("v", dim4, new float[] {2, 2, 2, 2})));

      // The same document must still be rejected. On main it is accepted, and its vector is
      // stored truncated to the first 4 components.
      long seqNo = -1;
      IllegalArgumentException expected = null;
      try {
        seqNo = w.addDocument(List.of(new KnnFloatVectorField("v", eightDims, dim8)));
      } catch (IllegalArgumentException e) {
        expected = e;
      }
      if (expected == null) {
        w.commit();
        StringBuilder stored = new StringBuilder();
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          for (LeafReaderContext ctx : r.leaves()) {
            FloatVectorValues values = ctx.reader().getFloatVectorValues("v");
            for (int ord = 0; ord < values.size(); ord++) {
              stored.append(java.util.Arrays.toString(values.vectorValue(ord))).append(' ');
            }
          }
        }
        fail(
            "8-dimensional vector accepted into a 4-dimensional field (seqNo="
                + seqNo
                + "); stored vectors: "
                + stored);
      }
      assertTrue(expected.getMessage(), expected.getMessage().contains("vector dimension"));
    }
  }

  public void testDocValuesTypeMismatchNotReportedAsSchemaError() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newConfig())) {
      w.addBatch(oneDocBatch(numericColumn("f", 1)));

      // Correctly rejected: "f" has NUMERIC doc values in this index.
      expectThrows(
          IllegalArgumentException.class,
          () -> w.addDocument(List.of(new SortedDocValuesField("f", new BytesRef("a")))));

      w.addBatch(oneDocBatch(numericColumn("f", 2)));

      // The same document must still be rejected with the schema error.
      expectThrows(
          IllegalArgumentException.class,
          () -> w.addDocument(List.of(new SortedDocValuesField("f", new BytesRef("b")))));
    }
  }
}
