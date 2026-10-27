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

import static org.apache.lucene.document.column.ColumnBatchTestUtil.simpleBatch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.KnnVectorsWriter;
import org.apache.lucene.codecs.hnsw.FlatFieldVectorsWriter;
import org.apache.lucene.codecs.hnsw.FlatVectorScorerUtil;
import org.apache.lucene.codecs.hnsw.FlatVectorsScorer;
import org.apache.lucene.codecs.lucene104.Lucene104ScalarQuantizedVectorScorer;
import org.apache.lucene.codecs.lucene104.Lucene104ScalarQuantizedVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104ScalarQuantizedVectorsWriter;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatchTestUtil.ArrayDenseFloatVectorColumn;
import org.apache.lucene.document.column.ColumnBatchTestUtil.ArrayFloatVectorColumn;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocsWithFieldSet;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.tests.store.BaseDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.quantization.QuantizedByteVectorValues.ScalarEncoding;

/**
 * A DENSE vector batch that addBatch rejects part-way must leave the HNSW and scalar-quantized
 * writers consistent when their flat field writer keeps the default addDenseValues, which adds the
 * vectors before the rejected one (one addValue per vector).
 */
public class TestDenseBatchCustomFlatFieldWriter extends LuceneTestCase {

  private static final FlatVectorsScorer SCORER =
      FlatVectorScorerUtil.getLucene99FlatVectorsScorer();

  /**
   * Per-field storage for the strategyFactory constructor of Lucene99FlatVectorsWriter. Like the
   * paged writer of TestKnnVectorsFormatCustomWriter, it implements addValue only.
   */
  private static final class ListFieldVectorsWriter extends FlatFieldVectorsWriter<float[]> {
    private final int dim;
    private final List<float[]> vectors = new ArrayList<>();
    private final DocsWithFieldSet docsWithField = new DocsWithFieldSet();
    private boolean finished;

    ListFieldVectorsWriter(FieldInfo fieldInfo) {
      this.dim = fieldInfo.getVectorDimension();
    }

    @Override
    public void addValue(int docID, float[] vectorValue) {
      vectors.add(copyValue(vectorValue));
      docsWithField.add(docID);
    }

    @Override
    public float[] copyValue(float[] vectorValue) {
      return ArrayUtil.copyOfSubArray(vectorValue, 0, dim);
    }

    @Override
    public List<float[]> getVectors() {
      return vectors;
    }

    @Override
    public DocsWithFieldSet getDocsWithFieldSet() {
      return docsWithField;
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
      return (long) vectors.size() * dim * Float.BYTES;
    }
  }

  /** The core HNSW writer over the list storage; the on-disk format is the stock one. */
  private static KnnVectorsFormat hnswOverListStorage(int tinySegmentsThreshold) {
    return new KnnVectorsFormat("Lucene99HnswVectorsFormat") {
      @Override
      public KnnVectorsWriter fieldsWriter(SegmentWriteState state) throws IOException {
        return new Lucene99HnswVectorsWriter(
            state,
            Lucene99HnswVectorsFormat.DEFAULT_MAX_CONN,
            Lucene99HnswVectorsFormat.DEFAULT_BEAM_WIDTH,
            new Lucene99FlatVectorsFormat(SCORER),
            new Lucene99FlatVectorsWriter(state, SCORER, ListFieldVectorsWriter::new),
            Lucene99HnswVectorsFormat.DEFAULT_NUM_MERGE_WORKER,
            null,
            tinySegmentsThreshold);
      }

      @Override
      public KnnVectorsReader fieldsReader(SegmentReadState state) throws IOException {
        return new Lucene99HnswVectorsFormat().fieldsReader(state);
      }

      @Override
      public int getMaxDimensions(String fieldName) {
        return DEFAULT_MAX_DIMENSIONS;
      }
    };
  }

  /** The core scalar-quantized writer over the list storage. */
  private static KnnVectorsFormat scalarQuantizedOverListStorage() {
    return new KnnVectorsFormat("Lucene104ScalarQuantizedVectorsFormat") {
      @Override
      public KnnVectorsWriter fieldsWriter(SegmentWriteState state) throws IOException {
        return new Lucene104ScalarQuantizedVectorsWriter(
            state,
            ScalarEncoding.UNSIGNED_BYTE,
            new Lucene99FlatVectorsWriter(state, SCORER, ListFieldVectorsWriter::new),
            new Lucene104ScalarQuantizedVectorScorer(SCORER));
      }

      @Override
      public KnnVectorsReader fieldsReader(SegmentReadState state) throws IOException {
        return new Lucene104ScalarQuantizedVectorsFormat().fieldsReader(state);
      }

      @Override
      public int getMaxDimensions(String fieldName) {
        return DEFAULT_MAX_DIMENSIONS;
      }
    };
  }

  /**
   * Adds {@code before} one document at a time, then {@code batch} through addBatch (its last
   * vector is invalid, so addBatch throws and the batch's documents are deleted), then {@code
   * after}, commits, and runs CheckIndex. The rejection is not tragic, so commit and CheckIndex
   * must succeed.
   */
  private void indexRejectedBatchThenCommit(
      KnnVectorsFormat format,
      VectorSimilarityFunction similarity,
      boolean dense,
      float[][] before,
      float[][] batch,
      float[][] after)
      throws IOException {
    FieldType type = KnnFloatVectorField.createFieldType(2, similarity);
    int[] batchDocs = new int[batch.length];
    for (int i = 0; i < batch.length; i++) {
      batchDocs[i] = i;
    }
    Column column =
        dense
            ? new ArrayDenseFloatVectorColumn("v", type, batch)
            : new ArrayFloatVectorColumn("v", type, batchDocs, batch);
    try (BaseDirectoryWrapper dir = newDirectory()) {
      dir.setCheckIndexOnClose(false); // checked below, with fail-fast
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.alwaysKnnVectorsFormat(format))
              .setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (float[] v : before) {
          w.addDocument(List.of(new KnnFloatVectorField("v", v, type)));
        }
        expectThrows(
            IllegalArgumentException.class, () -> w.addBatch(simpleBatch(batch.length, column)));
        assertNull("the rejected batch must not close the writer", w.getTragicException());
        for (float[] v : after) {
          w.addDocument(List.of(new KnnFloatVectorField("v", v, type)));
        }
        w.commit();
      }
      // level 3 runs the HNSW graph check; fail-fast because without it CheckIndex reports an
      // HNSW graph error but still calls the index clean
      TestUtil.checkIndex(dir, CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS, true, false, null);
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(before.length + after.length, reader.numDocs());
      }
    }
  }

  /** {@code n} unit vectors, spread around the circle (golden angle). */
  private static float[][] unitVectors(int n, int offset) {
    float[][] vectors = new float[n][];
    for (int i = 0; i < n; i++) {
      double angle = (offset + i) * 2.399963229728653;
      vectors[i] = new float[] {(float) Math.cos(angle), (float) Math.sin(angle)};
    }
    return vectors;
  }

  private static final float[][] BATCH_ENDING_WITH_NAN = {{0f, 1f}, {1f, 1f}, {Float.NaN, 0f}};
  private static final float[][] BATCH_ENDING_WITH_ZERO = {{0f, 3f}, {4f, 4f}, {0f, 0f}};

  /** HNSW graph built from the first vector: 1 doc, a rejected 3-doc batch, 1 doc. */
  public void testHnswTinySegmentsThresholdZero() throws IOException {
    indexRejectedBatchThenCommit(
        hnswOverListStorage(0),
        VectorSimilarityFunction.EUCLIDEAN,
        true,
        new float[][] {{1f, 0f}},
        BATCH_ENDING_WITH_NAN,
        new float[][] {{-1f, 0f}});
  }

  /** The default tiny-segment threshold, as in TestKnnVectorsFormatCustomWriter. */
  public void testHnswDefaultThreshold() throws IOException {
    float[][] batch = unitVectors(4, 1000);
    batch[3] = new float[] {Float.POSITIVE_INFINITY, 0f};
    indexRejectedBatchThenCommit(
        hnswOverListStorage(Lucene99HnswVectorsFormat.HNSW_GRAPH_THRESHOLD),
        VectorSimilarityFunction.EUCLIDEAN,
        true,
        unitVectors(1000, 0),
        batch,
        unitVectors(10, 2000));
  }

  /** COSINE scalar quantization: the batch's zero vector is rejected. */
  public void testScalarQuantizedCosine() throws IOException {
    indexRejectedBatchThenCommit(
        scalarQuantizedOverListStorage(),
        VectorSimilarityFunction.COSINE,
        true,
        new float[][] {{2f, 0f}},
        BATCH_ENDING_WITH_ZERO,
        new float[][] {{0.6f, 0.8f}, {-5f, 1f}});
  }

  /** Controls: the same rows as a SPARSE column, and the stock formats with a DENSE column. */
  public void testControls() throws IOException {
    float[][] one = {{1f, 0f}};
    float[][] last = {{-1f, 0f}};
    indexRejectedBatchThenCommit(
        hnswOverListStorage(0),
        VectorSimilarityFunction.EUCLIDEAN,
        false,
        one,
        BATCH_ENDING_WITH_NAN,
        last);
    indexRejectedBatchThenCommit(
        new Lucene99HnswVectorsFormat(16, 100, 0),
        VectorSimilarityFunction.EUCLIDEAN,
        true,
        one,
        BATCH_ENDING_WITH_NAN,
        last);
    float[][] first = {{2f, 0f}};
    float[][] after = {{0.6f, 0.8f}, {-5f, 1f}};
    indexRejectedBatchThenCommit(
        scalarQuantizedOverListStorage(),
        VectorSimilarityFunction.COSINE,
        false,
        first,
        BATCH_ENDING_WITH_ZERO,
        after);
    indexRejectedBatchThenCommit(
        new Lucene104ScalarQuantizedVectorsFormat(),
        VectorSimilarityFunction.COSINE,
        true,
        first,
        BATCH_ENDING_WITH_ZERO,
        after);
  }
}
