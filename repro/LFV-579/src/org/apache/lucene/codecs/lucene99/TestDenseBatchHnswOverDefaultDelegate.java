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
package org.apache.lucene.codecs.lucene99;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.KnnVectorsWriter;
import org.apache.lucene.codecs.hnsw.FlatFieldVectorsWriter;
import org.apache.lucene.codecs.hnsw.FlatVectorScorerUtil;
import org.apache.lucene.codecs.hnsw.FlatVectorsScorer;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatch;
import org.apache.lucene.document.column.ObjectTupleCursor;
import org.apache.lucene.document.column.VectorColumn;
import org.apache.lucene.document.column.VectorValuesCursor;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.DocsWithFieldSet;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestDenseBatchHnswOverDefaultDelegate extends LuceneTestCase {

  /** Per-field vector storage that keeps the default addDenseValues (one addValue per vector). */
  private static final class ListFieldVectorsWriter extends FlatFieldVectorsWriter<float[]> {
    private final List<float[]> vectors = new ArrayList<>();
    private final DocsWithFieldSet docs = new DocsWithFieldSet();
    private boolean finished;

    @Override
    public void addValue(int docID, float[] vector) {
      vectors.add(vector.clone());
      docs.add(docID);
    }

    @Override
    public float[] copyValue(float[] vector) {
      return vector.clone();
    }

    @Override
    public List<float[]> getVectors() {
      return vectors;
    }

    @Override
    public DocsWithFieldSet getDocsWithFieldSet() {
      return docs;
    }

    @Override
    public void finish() {
      finished = true;
    }

    @Override
    public boolean isFinished() {
      return finished;
    }

    @Override
    public long ramBytesUsed() {
      return 0;
    }
  }

  /** The stock HNSW writer over that storage, building the graph from the first vector. */
  private static final class ListHnswFormat extends KnnVectorsFormat {
    ListHnswFormat() {
      super("Lucene99HnswVectorsFormat");
    }

    @Override
    public KnnVectorsWriter fieldsWriter(SegmentWriteState state) throws IOException {
      FlatVectorsScorer scorer = FlatVectorScorerUtil.getLucene99FlatVectorsScorer();
      return new Lucene99HnswVectorsWriter(
          state,
          16,
          100,
          new Lucene99FlatVectorsFormat(scorer),
          new Lucene99FlatVectorsWriter(state, scorer, fi -> new ListFieldVectorsWriter()),
          1,
          null,
          0);
    }

    @Override
    public KnnVectorsReader fieldsReader(SegmentReadState state) throws IOException {
      return new Lucene99HnswVectorsFormat().fieldsReader(state);
    }

    @Override
    public int getMaxDimensions(String fieldName) {
      return 1024;
    }
  }

  public void testCommitAfterRejectedDenseBatch() throws Exception {
    FieldType type = KnnFloatVectorField.createFieldType(2, VectorSimilarityFunction.EUCLIDEAN);
    float[][] rows = {{0, 1}, {1, 1}, {Float.NaN, 0}};
    VectorColumn<float[]> column =
        new VectorColumn<>("v", type, Column.Density.DENSE) {
          @Override
          public ObjectTupleCursor<float[]> tuples() {
            throw new UnsupportedOperationException();
          }

          @Override
          public VectorValuesCursor<float[]> values() {
            return new VectorValuesCursor<>(rows.length, 2) {
              int i;

              @Override
              public float[] next() {
                return rows[i++];
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
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.alwaysKnnVectorsFormat(new ListHnswFormat()))
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(List.of(new KnnFloatVectorField("v", new float[] {1, 0}, type)));
        expectThrows(IllegalArgumentException.class, () -> w.addBatch(batch));
        w.addDocument(List.of(new KnnFloatVectorField("v", new float[] {-1, 0}, type)));
        // expected no error; actual: "Level 0 expects to have all nodes" (-ea), EOF in CheckIndex (-da)
        w.commit();
      }
      TestUtil.checkIndex(dir, CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS, true, false, null);
    }
  }
}
