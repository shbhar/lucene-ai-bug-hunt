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
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.tests.codecs.asserting.AssertingCodec;
import org.apache.lucene.util.NamedThreadFactory;

/**
 * Lucene99HnswVectorsFormat validates maxConn and beamWidth ("must be positive") and rejects
 * numMergeWorkers == 1 together with an executor, but it accepts numMergeWorkers <= 0 together
 * with an executor. A format that accepts its parameters must then be able to merge: here every
 * merge of a vector field with a graph fails.
 */
public class TestHnswNonPositiveMergeWorkers extends LuceneTestCase {

  public void testZeroMergeWorkersWithExecutor() throws Exception {
    assertRejectedOrMerges(0);
  }

  public void testNegativeMergeWorkersWithExecutor() throws Exception {
    assertRejectedOrMerges(-1);
  }

  /** 9.x test-framework has no TestUtil.alwaysKnnVectorsFormat; same construction by hand. */
  private static Codec alwaysFormat(KnnVectorsFormat format) {
    return new AssertingCodec() {
      @Override
      public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
        return format;
      }
    };
  }

  private void assertRejectedOrMerges(int numMergeWorkers) throws Exception {
    ExecutorService exec = Executors.newFixedThreadPool(2, new NamedThreadFactory("hnsw-merge"));
    try {
      KnnVectorsFormat format;
      try {
        // 9.x has no tiny-segment threshold: every segment with vectors gets a graph
        format = new Lucene99HnswVectorsFormat(16, 100, numMergeWorkers, exec);
      } catch (IllegalArgumentException expected) {
        return; // rejecting the parameter up front is a correct outcome
      }
      try (Directory dir = newDirectory()) {
        IndexWriterConfig iwc =
            new IndexWriterConfig()
                .setCodec(alwaysFormat(format))
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
