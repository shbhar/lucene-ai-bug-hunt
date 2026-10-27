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
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104ScalarQuantizedVectorsFormat;
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * LeafReader#searchNearestVectors documents {@code visitedLimit} as "the maximum number of nodes
 * that the search is allowed to visit", and the TopDocs total hits as "the number of documents
 * visited during the search". The exhaustive (non-graph) kNN search checks the limit before each
 * vector but counts visits only per batch of 64, so it visits and collects up to 64 vectors whatever
 * the limit.
 */
public class TestExhaustiveKnnVisitedLimit extends LuceneTestCase {

  private static final String FIELD = "v";

  private static Directory index(int numDocs, int maxConn, int tinySegmentsThreshold)
      throws IOException {
    return index(numDocs, new Lucene99HnswVectorsFormat(maxConn, 100, tinySegmentsThreshold));
  }

  private static Directory index(int numDocs, KnnVectorsFormat format) throws IOException {
    return index(numDocs, TestUtil.alwaysKnnVectorsFormat(format));
  }

  private static Directory index(int numDocs, Codec codec) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(codec);
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

  /** Exhaustive path (no graph, or k >= #vectors): limit checked per vector, counted per 64. */
  public void testExhaustivePathHonoursVisitedLimit() throws IOException {
    try (Directory dir = index(50, 16, 100); // 50 vectors: too few for a graph (built only when n > 100 * ln(n)), exhaustive scoring
        DirectoryReader reader = DirectoryReader.open(dir)) {
      LeafReader leaf = getOnlyLeafReader(reader);
      int visitedLimit = 1;
      TopDocs td =
          leaf.searchNearestVectors(
              FIELD, QUERY, 5, AcceptDocs.fromLiveDocs(null, leaf.maxDoc()), visitedLimit);
      assertEquals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO, td.totalHits.relation());
      assertTrue(
          "visitedLimit="
              + visitedLimit
              + " but the search visited "
              + td.totalHits.value()
              + " vectors and returned "
              + td.scoreDocs.length
              + " hits",
          td.totalHits.value() <= visitedLimit);
    }
  }

  /** The production default codec (TestUtil.getDefaultCodec(), not the randomized test codec). */
  public void testDefaultCodecHonoursVisitedLimit() throws IOException {
    try (Directory dir = index(50, TestUtil.getDefaultCodec());
        DirectoryReader reader = DirectoryReader.open(dir)) {
      LeafReader leaf = getOnlyLeafReader(reader);
      int visitedLimit = 1;
      TopDocs td =
          leaf.searchNearestVectors(
              FIELD, QUERY, 5, AcceptDocs.fromLiveDocs(null, leaf.maxDoc()), visitedLimit);
      assertTrue(
          "default codec: visitedLimit="
              + visitedLimit
              + " but the search visited "
              + td.totalHits.value()
              + " vectors and returned "
              + td.scoreDocs.length
              + " hits",
          td.totalHits.value() <= visitedLimit);
    }
  }

  /** Flat (no graph) scalar-quantized format: same per-64 batching in its exhaustive search. */
  public void testFlatQuantizedFormatHonoursVisitedLimit() throws IOException {
    try (Directory dir = index(50, new Lucene104ScalarQuantizedVectorsFormat());
        DirectoryReader reader = DirectoryReader.open(dir)) {
      LeafReader leaf = getOnlyLeafReader(reader);
      int visitedLimit = 1;
      TopDocs td =
          leaf.searchNearestVectors(
              FIELD, QUERY, 5, AcceptDocs.fromLiveDocs(null, leaf.maxDoc()), visitedLimit);
      assertTrue(
          "visitedLimit="
              + visitedLimit
              + " but the search visited "
              + td.totalHits.value()
              + " vectors and returned "
              + td.scoreDocs.length
              + " hits",
          td.totalHits.value() <= visitedLimit);
    }
  }
}
