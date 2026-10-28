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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

/**
 * An index whose HNSW graph was written with {@code Lucene99HnswVectorsFormat(maxConn = M_old)} is
 * reopened by an IndexWriter whose format is configured with a different {@code maxConn = M_new}.
 * New vectors are added and {@code forceMerge(1)} runs. The merge reuses the old segment's graph
 * (the largest segment, no deletions).
 *
 * <p>Expected (any Lucene version): the merge succeeds, the IndexWriter stays open, and the merged
 * segment can be searched with kNN queries. The format documents {@code maxConn} as a writer
 * setting ("the maximum number of connections to a node in the HNSW graph") and nothing documents
 * that it must stay the same for the life of an index; the .vem file records, per segment, "the
 * maximum number of connections (neighbours) that each node can have".
 *
 * <p>Only public API is used. 4,000 random 128-dimensional vectors; the queries use k = 10, so the
 * HNSW graph is walked (no exact-search fallback).
 */
public class TestHnswMergeReuseChangedMaxConn extends LuceneTestCase {

  private static final String FIELD = "vec";
  private static final int DIM = 128;
  private static final int N_OLD = 3000;
  private static final int N_NEW = 1000;
  private static final int BEAM = 100;
  private static final int NUM_QUERIES = 50;
  private static final int K = 10;

  private static final int DEFAULT_M = Lucene99HnswVectorsFormat.DEFAULT_MAX_CONN; // 16

  /** Raising maxConn from the default 16 to 32, single-threaded merger. */
  public void testRaisedMaxConn() throws Exception {
    changeMaxConnMergeAndSearch(DEFAULT_M, 32, false);
  }

  /** Raising maxConn 16 -> 32, concurrent merger (numMergeWorkers = 2 with an executor). */
  public void testRaisedMaxConnConcurrentMerger() throws Exception {
    changeMaxConnMergeAndSearch(DEFAULT_M, 32, true);
  }

  /** Lowering maxConn from 32 back to the default 16, single-threaded merger. */
  public void testLoweredMaxConn() throws Exception {
    changeMaxConnMergeAndSearch(32, DEFAULT_M, false);
  }

  /** Lowering maxConn 32 -> 16, concurrent merger (numMergeWorkers = 2 with an executor). */
  public void testLoweredMaxConnConcurrentMerger() throws Exception {
    changeMaxConnMergeAndSearch(32, DEFAULT_M, true);
  }

  /** Control: the same pipeline with an unchanged maxConn passes on every version. */
  public void testControlUnchangedMaxConn() throws Exception {
    changeMaxConnMergeAndSearch(DEFAULT_M, DEFAULT_M, false);
    changeMaxConnMergeAndSearch(32, 32, false);
  }

  /**
   * The same mismatch without changing any index's configuration: addIndexes(CodecReader...) of an
   * index built with the default maxConn (16, two segments) into a writer configured with 32.
   */
  public void testAddIndexesRaisedMaxConn() throws Exception {
    addIndexesWithDifferentMaxConn(DEFAULT_M, 32);
  }

  /** As above, into a writer configured with maxConn 8. */
  public void testAddIndexesLoweredMaxConn() throws Exception {
    addIndexesWithDifferentMaxConn(DEFAULT_M, 8);
  }

  private void addIndexesWithDifferentMaxConn(int mSrc, int mDst) throws Exception {
    Random r = new Random(random().nextLong());
    float[][] vectors = randomVectors(r, N_OLD + N_NEW);
    String what = "addIndexes(CodecReader...) maxConn " + mSrc + " -> " + mDst;
    try (Directory src = FSDirectory.open(createTempDir("src"));
        Directory dst = FSDirectory.open(createTempDir("dst"))) {
      // source index: maxConn = mSrc, two segments (2000 + 1000 vectors)
      try (IndexWriter w = new IndexWriter(src, config(mSrc, null))) {
        addVectors(w, vectors, 0, 2000);
        w.commit();
        addVectors(w, vectors, 2000, N_OLD);
        w.commit();
      }
      // target index: maxConn = mDst, one segment of its own, then addIndexes of the source
      List<String> hnswLog = new ArrayList<>();
      IndexWriterConfig dstConfig = config(mDst, null);
      dstConfig.setInfoStream(new HnswLog(hnswLog));
      IndexWriter w = new IndexWriter(dst, dstConfig);
      try {
        addVectors(w, vectors, N_OLD, N_OLD + N_NEW);
        w.commit();
        try (DirectoryReader srcReader = DirectoryReader.open(src)) {
          assertEquals(2, srcReader.leaves().size());
          CodecReader[] leaves = new CodecReader[srcReader.leaves().size()];
          for (int i = 0; i < leaves.length; i++) {
            leaves[i] = (CodecReader) srcReader.leaves().get(i).reader();
          }
          try {
            w.addIndexes(leaves);
          } catch (Throwable t) {
            throw new AssertionError(
                what
                    + " failed (writer open="
                    + w.isOpen()
                    + ", tragic="
                    + w.getTragicException()
                    + ")",
                t);
          }
        }
        assertTrue(what + ": merge did not reuse a graph: " + hnswLog, reused(hnswLog));
        w.commit();
      } finally {
        if (w.isOpen()) {
          w.close();
        }
      }
      searchEveryLeaf(dst, vectors, r, what);
    }
  }

  // ------------------------------------------------------------------------------------------

  private void changeMaxConnMergeAndSearch(int mOld, int mNew, boolean concurrentMerger)
      throws Exception {
    Random r = new Random(random().nextLong());
    float[][] vectors = randomVectors(r, N_OLD + N_NEW);
    ExecutorService exec = concurrentMerger ? Executors.newFixedThreadPool(2) : null;
    String what =
        "maxConn " + mOld + " -> " + mNew + (concurrentMerger ? ", concurrent merger" : "");
    try (Directory dir = FSDirectory.open(createTempDir("maxconn"))) {
      // session 1: N_OLD vectors with maxConn = mOld, one segment
      try (IndexWriter w = new IndexWriter(dir, config(mOld, exec))) {
        addVectors(w, vectors, 0, N_OLD);
        w.forceMerge(1);
      }
      // session 2: same index, maxConn = mNew, N_NEW more vectors, then forceMerge(1)
      List<String> hnswLog = new ArrayList<>();
      IndexWriterConfig config = config(mNew, exec);
      config.setOpenMode(IndexWriterConfig.OpenMode.APPEND);
      config.setInfoStream(new HnswLog(hnswLog));
      IndexWriter w = new IndexWriter(dir, config);
      try {
        addVectors(w, vectors, N_OLD, N_OLD + N_NEW);
        w.commit();
        try {
          w.forceMerge(1);
        } catch (Throwable t) {
          throw new AssertionError(
              what
                  + ": forceMerge(1) failed (writer open="
                  + w.isOpen()
                  + ", tragic="
                  + w.getTragicException()
                  + ")",
              t);
        }
        assertTrue(what + ": writer must stay open", w.isOpen());
        if (concurrentMerger == false) {
          // vacuity probe: the merge reused the old graph instead of rebuilding from scratch
          assertTrue(what + ": merge did not reuse a graph: " + hnswLog, reused(hnswLog));
        }
        w.commit();
      } finally {
        if (w.isOpen()) {
          w.close();
        }
      }
      searchEveryLeaf(dir, vectors, r, what);
    } finally {
      if (exec != null) {
        exec.shutdown();
        assertTrue(exec.awaitTermination(1, TimeUnit.MINUTES));
      }
    }
  }

  /** Runs CheckIndex (recorded in the message) and NUM_QUERIES kNN queries over the index. */
  private static void searchEveryLeaf(Directory dir, float[][] vectors, Random r, String what)
      throws IOException {
    boolean checkIndexClean;
    ByteArrayOutputStream checkOut = new ByteArrayOutputStream();
    try (CheckIndex checker = new CheckIndex(dir)) {
      checker.setInfoStream(new PrintStream(checkOut, true, StandardCharsets.UTF_8));
      checkIndexClean = checker.checkIndex().clean;
    }
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(what, N_OLD + N_NEW, reader.numDocs());
      IndexSearcher searcher = new IndexSearcher(reader);
      for (int q = 0; q < NUM_QUERIES; q++) {
        float[] query = randomVector(r);
        TopDocs hits;
        try {
          hits = searcher.search(new KnnFloatVectorQuery(FIELD, query, K), K);
        } catch (Throwable t) {
          throw new AssertionError(
              what
                  + ": kNN query "
                  + q
                  + " on the merged index failed; segments="
                  + reader.leaves().size()
                  + ", CheckIndex clean="
                  + checkIndexClean,
              t);
        }
        assertEquals(what + ": hits for query " + q, K, hits.scoreDocs.length);
      }
      for (LeafReaderContext ctx : reader.leaves()) {
        assertEquals(what, ctx.reader().maxDoc(), ctx.reader().getFloatVectorValues(FIELD).size());
      }
    }
  }

  private static IndexWriterConfig config(int maxConn, ExecutorService exec) {
    final KnnVectorsFormat format =
        exec == null
            ? new Lucene99HnswVectorsFormat(maxConn, BEAM)
            : new Lucene99HnswVectorsFormat(maxConn, BEAM, 2, exec);
    // the documented per-field hook of the default codec ("Returns the vectors format that should
    // be used for writing new segments of field")
    Codec codec =
        new Lucene104Codec() {
          @Override
          public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
            return format;
          }
        };
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(codec);
    iwc.setRAMBufferSizeMB(64);
    // merges run in the calling thread, so a failure surfaces in forceMerge/addIndexes itself
    // (with the default ConcurrentMergeScheduler the same exception comes from a merge thread)
    iwc.setMergeScheduler(new SerialMergeScheduler());
    return iwc;
  }

  private static void addVectors(IndexWriter w, float[][] vectors, int from, int to)
      throws IOException {
    for (int i = from; i < to; i++) {
      Document doc = new Document();
      doc.add(new KnnFloatVectorField(FIELD, vectors[i], VectorSimilarityFunction.EUCLIDEAN));
      w.addDocument(doc);
    }
  }

  private static float[][] randomVectors(Random r, int n) {
    float[][] v = new float[n][];
    for (int i = 0; i < n; i++) {
      v[i] = randomVector(r);
    }
    return v;
  }

  private static float[] randomVector(Random r) {
    float[] v = new float[DIM];
    for (int d = 0; d < DIM; d++) {
      v[d] = r.nextFloat();
    }
    return v;
  }

  private static boolean reused(List<String> hnswLog) {
    for (String m : hnswLog) {
      if (m.contains("build graph from merging")) {
        return true;
      }
    }
    return false;
  }

  /** Collects the HNSW component's info-stream messages (public IndexWriterConfig API). */
  private static final class HnswLog extends InfoStream {
    private final List<String> messages;

    HnswLog(List<String> messages) {
      this.messages = messages;
    }

    @Override
    public synchronized void message(String component, String message) {
      messages.add(message);
    }

    @Override
    public boolean isEnabled(String component) {
      return "HNSW".equals(component);
    }

    @Override
    public void close() {}
  }
}
