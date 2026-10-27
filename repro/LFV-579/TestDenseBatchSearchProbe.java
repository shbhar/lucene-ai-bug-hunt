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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.tests.store.BaseDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.quantization.QuantizedByteVectorValues.ScalarEncoding;

/**
 * A DENSE vector batch that addBatch rejects part-way must leave the HNSW and scalar-quantized
 * writers consistent when their flat field writer keeps the default addDenseValues, which adds
 * the vectors before the rejected one (one addValue per vector).
 */
public class TestDenseBatchSearchProbe extends LuceneTestCase {

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


  /** Same steps as the paste-ready test; after the commit, query every live document's own vector. */
  private void probe(String name, KnnVectorsFormat format, VectorSimilarityFunction similarity,
      float[][] before, float[][] batch, float[][] after) throws IOException {
    FieldType type = KnnFloatVectorField.createFieldType(2, similarity);
    try (BaseDirectoryWrapper dir = newDirectory()) {
      dir.setCheckIndexOnClose(false);
      IndexWriterConfig iwc = new IndexWriterConfig()
          .setCodec(TestUtil.alwaysKnnVectorsFormat(format)).setMergePolicy(NoMergePolicy.INSTANCE);
      String commit = "commit ok";
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (float[] v : before) {
          w.addDocument(List.of(new KnnFloatVectorField("v", v, type)));
        }
        expectThrows(IllegalArgumentException.class,
            () -> w.addBatch(simpleBatch(batch.length, new ArrayDenseFloatVectorColumn("v", type, batch))));
        for (float[] v : after) {
          w.addDocument(List.of(new KnnFloatVectorField("v", v, type)));
        }
        try {
          w.commit();
        } catch (Throwable t) {
          commit = "commit threw " + t + "; writer open=" + w.isOpen();
        }
      }
      StringBuilder sb = new StringBuilder(name + ": " + commit);
      if (commit.equals("commit ok")) {
        float[][] live = new float[before.length + after.length][];
        System.arraycopy(before, 0, live, 0, before.length);
        System.arraycopy(after, 0, live, before.length, after.length);
        int[] liveDoc = new int[live.length];
        for (int i = 0; i < live.length; i++) {
          liveDoc[i] = i < before.length ? i : i + batch.length;
        }
        try (DirectoryReader reader = DirectoryReader.open(dir)) {
          IndexSearcher searcher = new IndexSearcher(reader);
          int threw = 0, wrong = 0, wrongInAfter = 0;
          String firstThrow = null, firstWrong = null;
          for (int i = 0; i < live.length; i++) {
            try {
              ScoreDoc[] hits = searcher.search(new KnnFloatVectorQuery("v", live[i], 1), 1).scoreDocs;
              if (hits.length != 1 || hits[0].doc != liveDoc[i]) {
                wrong++;
                if (i >= before.length) wrongInAfter++;
                if (firstWrong == null) {
                  firstWrong = "doc " + liveDoc[i] + " -> " + (hits.length == 0 ? "no hit" : "doc " + hits[0].doc);
                }
              }
            } catch (Throwable t) {
              threw++;
              if (firstThrow == null) firstThrow = t.toString();
            }
          }
          sb.append("; numDocs=" + reader.numDocs() + "; k=1 own-vector queries=" + live.length
              + ", threw=" + threw + (firstThrow == null ? "" : " (" + firstThrow + ")")
              + ", wrong top hit=" + wrong + " (of the " + after.length + " docs added after the batch: " + wrongInAfter + ")"
              + (firstWrong == null ? "" : ", e.g. " + firstWrong));
        }
        try {
          TestUtil.checkIndex(dir, CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS, true, false, null);
          sb.append("; CheckIndex level 3 fail-fast: clean");
        } catch (Throwable t) {
          sb.append("; CheckIndex level 3 fail-fast threw " + t);
        }
        try {
          TestUtil.checkIndex(dir, CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS, false, false, null);
          sb.append("; CheckIndex level 3 no fail-fast: clean");
        } catch (Throwable t) {
          sb.append("; CheckIndex level 3 no fail-fast threw " + t);
        }
      }
      System.out.println("PROBE " + sb);
    }
  }

  private static float[][] unitVectors(int n, int offset) {
    float[][] vectors = new float[n][];
    for (int i = 0; i < n; i++) {
      double angle = (offset + i) * 2.399963229728653;
      vectors[i] = new float[] {(float) Math.cos(angle), (float) Math.sin(angle)};
    }
    return vectors;
  }

  public void testProbe() throws IOException {
    probe("hnsw-threshold0", hnswOverListStorage(0), VectorSimilarityFunction.EUCLIDEAN,
        new float[][] {{1f, 0f}}, new float[][] {{0f, 1f}, {1f, 1f}, {Float.NaN, 0f}}, new float[][] {{-1f, 0f}});
    float[][] batch = unitVectors(4, 1000);
    batch[3] = new float[] {Float.POSITIVE_INFINITY, 0f};
    probe("hnsw-default-threshold", hnswOverListStorage(Lucene99HnswVectorsFormat.HNSW_GRAPH_THRESHOLD),
        VectorSimilarityFunction.EUCLIDEAN, unitVectors(1000, 0), batch, unitVectors(10, 2000));
    probe("sq-cosine", scalarQuantizedOverListStorage(), VectorSimilarityFunction.COSINE,
        new float[][] {{2f, 0f}}, new float[][] {{0f, 3f}, {4f, 4f}, {0f, 0f}}, new float[][] {{0.6f, 0.8f}, {-5f, 1f}});
    probe("sq-cosine-finder-rows", scalarQuantizedOverListStorage(), VectorSimilarityFunction.COSINE,
        new float[][] {{1f, 0f}}, new float[][] {{0f, 1f}, {1f, 1f}, {0f, 0f}}, new float[][] {{-1f, 0.5f}});
  }
}
