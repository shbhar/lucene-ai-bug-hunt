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
package org.apache.lucene.codecs.lucene104;

import java.util.List;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatch;
import org.apache.lucene.document.column.ObjectTupleCursor;
import org.apache.lucene.document.column.VectorColumn;
import org.apache.lucene.document.column.VectorValuesCursor;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.VectorEncoding;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Main-only companion of TestOsqAntipodalZeroCentroid: the same flush failure through the
 * column-oriented IndexWriter#addBatch with a DENSE vector column (the bulk addDenseValues path).
 */
public class TestOsqAntipodalZeroCentroidColumnar extends LuceneTestCase {

  public void testDenseColumnFlushOfAntipodalPair() throws Exception {
    float[][] rows = {{1f, 0f}, {-1f, 0f}};
    FieldType type = new FieldType();
    type.setVectorAttributes(2, VectorEncoding.FLOAT32, VectorSimilarityFunction.COSINE);
    type.freeze();
    VectorColumn<float[]> column =
        new VectorColumn<>("vec", type, Column.Density.DENSE) {
          @Override
          public ObjectTupleCursor<float[]> tuples() {
            return new ObjectTupleCursor<>() {
              int pos = -1;

              @Override
              public int nextDoc() {
                return ++pos < rows.length ? pos : DocIdSetIterator.NO_MORE_DOCS;
              }

              @Override
              public float[] value() {
                return rows[pos];
              }
            };
          }

          @Override
          public VectorValuesCursor<float[]> values() {
            return new VectorValuesCursor<>(rows.length, 2) {
              int pos;

              @Override
              public float[] next() {
                if (pos >= size()) throw new IllegalStateException("overrun");
                return rows[pos++];
              }
            };
          }
        };
    ColumnBatch batch =
        new ColumnBatch() {
          @Override
          public int numDocs() {
            return rows.length;
          }

          @Override
          public Iterable<Column> columns() {
            return List.of(column);
          }
        };

    KnnVectorsFormat format = new Lucene104ScalarQuantizedVectorsFormat();
    IndexWriterConfig c = new IndexWriterConfig();
    c.setCodec(
        new Lucene104Codec() {
          @Override
          public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
            return format;
          }
        });
    c.setMergePolicy(NoMergePolicy.INSTANCE);
    c.setMergeScheduler(new SerialMergeScheduler());
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, c);
      try {
        w.addBatch(batch);
        try {
          w.commit();
        } catch (Throwable t) {
          throw new AssertionError(
              "commit (flush) of a dense column batch must succeed for legal vectors, but threw "
                  + t
                  + " (writer open="
                  + w.isOpen()
                  + ")",
              t);
        }
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          assertEquals(2, r.numDocs());
        }
      } finally {
        if (w.isOpen()) w.rollback();
      }
    }
  }
}
