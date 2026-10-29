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
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatch;
import org.apache.lucene.document.column.ObjectTupleCursor;
import org.apache.lucene.document.column.VectorColumn;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestAddBatchRejectedFrozenType extends LuceneTestCase {

  public void testWrongDimensionStaysRejectedAfterAddBatch() throws Exception {
    FieldType dim4 = KnnFloatVectorField.createFieldType(4, VectorSimilarityFunction.EUCLIDEAN);
    FieldType dim8 = KnnFloatVectorField.createFieldType(8, VectorSimilarityFunction.EUCLIDEAN);
    List<KnnFloatVectorField> bad = List.of(new KnnFloatVectorField("v", new float[8], dim8));
    ColumnBatch oneDim4Vector =
        new ColumnBatch() {
          @Override
          public int numDocs() {
            return 1;
          }

          @Override
          public Iterable<Column> columns() {
            return List.of(
                new VectorColumn<float[]>("v", dim4, Column.Density.SPARSE) {
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
                        return new float[4];
                      }
                    };
                  }
                });
          }
        };
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      w.addDocument(List.of(new KnnFloatVectorField("v", new float[4], dim4)));
      expectThrows(IllegalArgumentException.class, () -> w.addDocument(bad));
      w.addBatch(oneDim4Vector);
      // expected: the 8-dim document is rejected again; actual: it is accepted (stored truncated)
      expectThrows(IllegalArgumentException.class, () -> w.addDocument(bad));
    }
  }
}
