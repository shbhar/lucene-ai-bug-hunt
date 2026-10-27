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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104HnswScalarQuantizedVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.NamedThreadFactory;
import org.apache.lucene.util.quantization.QuantizedByteVectorValues.ScalarEncoding;

/**
 * Lucene99HnswVectorsFormat validates maxConn and beamWidth ("must be positive") and rejects
 * numMergeWorkers == 1 together with an executor, but it accepts numMergeWorkers <= 0 together
 * with an executor. A format that accepts its parameters must then be able to merge: here every
 * merge of a vector field with a graph fails.
 */
public class TestHnswNonPositiveMergeWorkers extends LuceneTestCase {

  /** A format factory that may reject its parameters with IllegalArgumentException. */
  interface FormatFactory {
    KnnVectorsFormat create(int numMergeWorkers, ExecutorService exec);
  }

  // tinySegmentsThreshold 0: always build a graph, so that small segments are enough
  static final FormatFactory HNSW =
      (workers, exec) -> new Lucene99HnswVectorsFormat(16, 100, workers, exec, 0);

  static final FormatFactory HNSW_OSQ =
      (workers, exec) ->
          new Lucene104HnswScalarQuantizedVectorsFormat(
              ScalarEncoding.UNSIGNED_BYTE,
              16,
              100,
              workers,
              exec,
              0);

  public void testZeroMergeWorkersWithExecutor() throws Exception {
    assertRejectedOrMerges(HNSW, 0, true);
  }

  public void testNegativeMergeWorkersWithExecutor() throws Exception {
    assertRejectedOrMerges(HNSW, -1, true);
  }

  public void testZeroMergeWorkersWithExecutorScalarQuantized() throws Exception {
    assertRejectedOrMerges(HNSW_OSQ, 0, true);
  }

  /** Control: the same value without an executor takes the serial merge path and works. */
  public void testZeroMergeWorkersWithoutExecutor() throws Exception {
    assertRejectedOrMerges(HNSW, 0, false);
  }

  private void assertRejectedOrMerges(FormatFactory factory, int numMergeWorkers, boolean withExec)
      throws Exception {
    ExecutorService exec = Executors.newFixedThreadPool(2, new NamedThreadFactory("hnsw-merge"));
    try {
      KnnVectorsFormat format;
      try {
        format = factory.create(numMergeWorkers, withExec ? exec : null);
      } catch (IllegalArgumentException expected) {
        return; // rejecting the parameter up front is a correct outcome
      }
      try (Directory dir = newDirectory()) {
        IndexWriterConfig iwc =
            new IndexWriterConfig()
                .setCodec(TestUtil.alwaysKnnVectorsFormat(format))
                .setMergePolicy(new LogDocMergePolicy())
                .setMergeScheduler(new SerialMergeScheduler())
                .setMaxBufferedDocs(1000);
        try (IndexWriter w = new IndexWriter(dir, iwc)) {
          for (int segment = 0; segment < 2; segment++) {
            for (int i = 0; i < 20; i++) {
              Document doc = new Document();
              doc.add(
                  new KnnFloatVectorField(
                      "v",
                      new float[] {random().nextFloat(), random().nextFloat()},
                      VectorSimilarityFunction.EUCLIDEAN));
              w.addDocument(doc);
            }
            w.flush();
          }
          // the format accepted numMergeWorkers, so a merge must work
          w.forceMerge(1);
        }
        try (DirectoryReader reader = DirectoryReader.open(dir)) {
          assertEquals(1, reader.leaves().size());
          assertEquals(40, reader.leaves().get(0).reader().getFloatVectorValues("v").size());
        }
      }
    } finally {
      exec.shutdown();
    }
  }
}
