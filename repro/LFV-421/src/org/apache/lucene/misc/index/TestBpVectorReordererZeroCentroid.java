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
package org.apache.lucene.misc.index;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * A partition whose vectors sum to zero (here: unit vectors and their opposites) has no direction.
 * BpVectorReorderer normalizes partition centroids for DOT_PRODUCT and COSINE fields; a zero
 * centroid must not become NaN (VectorUtil.dotProduct asserts finite results).
 */
public class TestBpVectorReordererZeroCentroid extends LuceneTestCase {

  /** 64 unit vectors alternating (1, 0) and (-1, 0), then 64 vectors (0, 1); all legal unit vectors. */
  private static void index(IndexWriter w) throws IOException {
    for (int i = 0; i < 128; i++) {
      Document doc = new Document();
      float[] v = i >= 64 ? new float[] {0, 1} : (i % 2 == 0 ? new float[] {1, 0} : new float[] {-1, 0});
      doc.add(new KnnFloatVectorField("v", v, VectorSimilarityFunction.DOT_PRODUCT));
      w.addDocument(doc);
    }
  }

  /** Direct call on one segment. */
  public void testComputeDocMap() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(
              dir,
              new IndexWriterConfig()
                  .setCodec(TestUtil.getDefaultCodec())
                  .setMergePolicy(NoMergePolicy.INSTANCE)
                  .setMaxBufferedDocs(10_000))) {
        index(w);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        CodecReader leaf = (CodecReader) reader.leaves().get(0).reader();
        assertNotNull(new BpVectorReorderer("v").computeDocMap(leaf, dir, null));
      }
    }
  }

  /** Through BPReorderingMergePolicy: the merge must succeed and keep the writer open. */
  public void testReorderingMerge() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w =
          new IndexWriter(
              dir,
              new IndexWriterConfig()
                  .setCodec(TestUtil.getDefaultCodec())
                  .setMergeScheduler(new SerialMergeScheduler())
                  .setMaxBufferedDocs(64)
                  .setMergePolicy(
                      new BPReorderingMergePolicy(
                          new LogDocMergePolicy(), new BpVectorReorderer("v"))));
      index(w);
      try {
        w.forceMerge(1);
      } finally {
        assertNull("the reordering merge closed the writer", w.getTragicException());
      }
      w.close();
    }
  }
}
