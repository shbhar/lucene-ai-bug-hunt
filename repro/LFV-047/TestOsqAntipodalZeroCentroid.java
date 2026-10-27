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

import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * COSINE vectors whose unit-scaled sum is exactly zero, such as {1,0} and {-1,0}, are legal
 * (finite, non-zero) and index fine with the default vectors format. With the scalar-quantized
 * (OSQ) formats the writer normalizes the zero centroid and throws, at flush and at merge. Each
 * test asserts the correct behaviour: the commit or merge succeeds and every document stays
 * searchable. Public API only; deterministic (in-memory directory, explicit codec, merge policy and
 * serial merge scheduler).
 */
public class TestOsqAntipodalZeroCentroid extends LuceneTestCase {

  private static final String F = "vec";
  private static final VectorSimilarityFunction COS = VectorSimilarityFunction.COSINE;

  private static IndexWriterConfig config(KnnVectorsFormat format, MergePolicy mergePolicy) {
    IndexWriterConfig c = new IndexWriterConfig();
    c.setCodec(
        new Lucene104Codec() {
          @Override
          public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
            return format;
          }
        });
    c.setMergePolicy(mergePolicy);
    c.setMergeScheduler(new SerialMergeScheduler());
    c.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    c.setRAMBufferSizeMB(64);
    return c;
  }

  private static LogDocMergePolicy mergeOnRequest() {
    LogDocMergePolicy mp = new LogDocMergePolicy();
    mp.setMinMergeDocs(1);
    return mp; // mergeFactor 10: two segments are merged only by forceMerge / forceMergeDeletes
  }

  private static void add(IndexWriter w, String id, float[] v, VectorSimilarityFunction sim)
      throws Exception {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.NO));
    d.add(new KnnFloatVectorField(F, v, sim));
    w.addDocument(d);
  }

  /** Runs the step and turns an exception into a failure that names the writer state. */
  private static void mustSucceed(String step, IndexWriter w, IORunnable r) {
    try {
      r.run();
    } catch (Throwable t) {
      throw new AssertionError(
          step
              + " must succeed for legal vectors, but threw "
              + t
              + " (writer open="
              + w.isOpen()
              + ", tragic exception="
              + w.getTragicException()
              + ")",
          t);
    }
  }

  private interface IORunnable {
    void run() throws Exception;
  }

  /** All docs are visible and a kNN query over the field returns every one of them. */
  private static void assertSearchable(Directory dir, int expectedDocs, int expectedLeaves)
      throws Exception {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals("live docs", expectedDocs, r.numDocs());
      assertEquals("segments", expectedLeaves, r.leaves().size());
      IndexSearcher s = new IndexSearcher(r);
      int hits =
          s.search(new KnnFloatVectorQuery(F, new float[] {0.6f, 0.8f}, 10), 10).scoreDocs.length;
      assertEquals("kNN hits", expectedDocs, hits);
    }
  }

  /** Control: the same vectors work without quantization, with OSQ + DOT_PRODUCT, and near-miss. */
  public void testControls() throws Exception {
    Object[][] cases = {
      {new Lucene99HnswVectorsFormat(), COS, new float[] {-1f, 0f}},
      {
        new Lucene104ScalarQuantizedVectorsFormat(),
        VectorSimilarityFunction.DOT_PRODUCT,
        new float[] {-1f, 0f}
      },
      {new Lucene104ScalarQuantizedVectorsFormat(), COS, new float[] {-1f, 1e-6f}},
    };
    for (Object[] c : cases) {
      try (Directory dir = new ByteBuffersDirectory();
          IndexWriter w =
              new IndexWriter(dir, config((KnnVectorsFormat) c[0], NoMergePolicy.INSTANCE))) {
        VectorSimilarityFunction sim = (VectorSimilarityFunction) c[1];
        add(w, "a", new float[] {1f, 0f}, sim);
        add(w, "b", (float[]) c[2], sim);
        w.commit();
        assertSearchable(dir, 2, 1);
      }
    }
  }

  /** Flush: computeCentroid normalizes the zero mean of {1,0} and {-1,0}. */
  public void testFlushOfAntipodalPair() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w =
          new IndexWriter(
              dir, config(new Lucene104ScalarQuantizedVectorsFormat(), NoMergePolicy.INSTANCE));
      try {
        add(w, "a", new float[] {1f, 0f}, COS);
        add(w, "b", new float[] {-1f, 0f}, COS);
        mustSucceed("commit (flush)", w, w::commit);
        assertSearchable(dir, 2, 1);
      } finally {
        if (w.isOpen()) w.rollback();
      }
    }
  }

  /** Merge, stored per-segment centroids (flat format): their count-weighted mean is zero. */
  public void testMergeOfAntipodalSegmentsStoredCentroids() throws Exception {
    checkMergeOfTwoOneDocSegments(new Lucene104ScalarQuantizedVectorsFormat());
  }

  /** Merge, centroid recomputed from the vectors (HNSW format): the mean is zero. */
  public void testMergeOfAntipodalSegmentsRecomputedCentroid() throws Exception {
    checkMergeOfTwoOneDocSegments(new Lucene104HnswScalarQuantizedVectorsFormat());
  }

  private void checkMergeOfTwoOneDocSegments(KnnVectorsFormat format) throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, config(format, mergeOnRequest()));
      try {
        add(w, "a", new float[] {0.6f, 0.8f}, COS);
        w.commit(); // segment 1: centroid (0.6, 0.8)
        add(w, "b", new float[] {-0.6f, -0.8f}, COS);
        w.commit(); // segment 2: centroid (-0.6, -0.8)
        assertSearchable(dir, 2, 2);
        mustSucceed("forceMerge(1)", w, () -> w.forceMerge(1));
        w.commit();
        assertSearchable(dir, 2, 1);
      } finally {
        if (w.isOpen()) w.rollback();
      }
    }
  }

  /** Merge after a delete leaves {1,0} and {-1,0} as the only live vectors. */
  public void testMergeAfterDeleteLeavesAntipodalPair() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w =
          new IndexWriter(
              dir, config(new Lucene104HnswScalarQuantizedVectorsFormat(), mergeOnRequest()));
      try {
        add(w, "a", new float[] {1f, 0f}, COS);
        add(w, "b", new float[] {-1f, 0f}, COS);
        add(w, "c", new float[] {0f, 1f}, COS);
        w.commit(); // centroid (0, 1): flush is fine
        w.deleteDocuments(new Term("id", "c"));
        w.commit();
        assertSearchable(dir, 2, 1);
        mustSucceed("forceMergeDeletes()", w, () -> w.forceMergeDeletes(true));
        w.commit();
        assertSearchable(dir, 2, 1);
      } finally {
        if (w.isOpen()) w.rollback();
      }
    }
  }
}
