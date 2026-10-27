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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.Random;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.knn.KnnSearchStrategy;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BitSetIterator;
import org.apache.lucene.util.FixedBitSet;

/**
 * LeafReader#searchNearestVectors documents {@code visitedLimit} as "the maximum number of nodes
 * that the search is allowed to visit", and the TopDocs total hits as "the number of documents
 * visited during the search". The HNSW entry phase (descent through the upper levels, and seeded
 * entry points) scores a whole batch of vectors before it counts them against the limit.
 */
public class TestHnswEntryPhaseVisitedLimit extends LuceneTestCase {

  private static final String FIELD = "v";

  private static Directory index(int numDocs, int maxConn, int tinySegmentsThreshold)
      throws IOException {
    return index(numDocs, new Lucene99HnswVectorsFormat(maxConn, 100, tinySegmentsThreshold));
  }

  private static Directory index(int numDocs, KnnVectorsFormat format) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.alwaysKnnVectorsFormat(format));
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    iwc.setMaxBufferedDocs(numDocs + 1); // one segment, one flush, sequential graph build
    iwc.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    Random r = new Random(42);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < numDocs; i++) {
        Document d = new Document();
        float[] v = {(float) r.nextGaussian(), (float) r.nextGaussian(), (float) r.nextGaussian()};
        d.add(new KnnFloatVectorField(FIELD, v, VectorSimilarityFunction.EUCLIDEAN));
        w.addDocument(d);
      }
      w.commit();
    }
    return dir;
  }

  private static final float[] QUERY = {0.25f, -0.5f, 1f};

  /** HNSW path: the upper-level (entry point) descent gathers a whole neighbour list first. */
  public void testGraphEntryPhaseHonoursVisitedLimit() throws IOException {
    try (Directory dir = index(3000, 16, 0);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      LeafReader leaf = getOnlyLeafReader(reader);
      StringBuilder violations = new StringBuilder();
      for (int visitedLimit = 0; visitedLimit <= 40; visitedLimit++) {
        TopDocs td =
            leaf.searchNearestVectors(
                FIELD, QUERY, 3, AcceptDocs.fromLiveDocs(null, leaf.maxDoc()), visitedLimit);
        if (td.totalHits.value() > visitedLimit) {
          violations
              .append(" visitedLimit=")
              .append(visitedLimit)
              .append(" visited=")
              .append(td.totalHits.value())
              .append(';');
        }
      }
      assertTrue("searches visited more vectors than allowed:" + violations, violations.isEmpty());
    }
  }

  /**
   * Seeded entry points are all scored, whatever the limit: with 100 seeds and visitLimit = 2 the
   * search visits 100 vectors, beyond even the "one 64-vector batch" tolerance that
   * BaseKnnVectorsFormatTestCase#testSearchWithVisitedLimit grants the exhaustive path.
   */
  public void testSeededEntryPointsHonourVisitLimit() throws IOException {
    try (Directory dir = index(3000, 16, 0);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      LeafReader leaf = getOnlyLeafReader(reader);
      // every document has a vector, so vector ordinal == doc id
      int numSeeds = 100;
      FixedBitSet seeds = new FixedBitSet(leaf.maxDoc());
      for (int i = 0; i < numSeeds; i++) seeds.set(i * 29);
      int visitLimit = 2;
      KnnSearchStrategy.Seeded seeded =
          new KnnSearchStrategy.Seeded(
              new BitSetIterator(seeds, numSeeds), numSeeds, KnnSearchStrategy.Hnsw.DEFAULT);
      TopKnnCollector collector = new TopKnnCollector(3, visitLimit, seeded);
      leaf.searchNearestVectors(
          FIELD, QUERY, collector, AcceptDocs.fromLiveDocs(null, leaf.maxDoc()));
      assertTrue(
          "visitLimit=" + visitLimit + " but the search visited " + collector.visitedCount(),
          collector.visitedCount() <= visitLimit);
    }
  }
}
