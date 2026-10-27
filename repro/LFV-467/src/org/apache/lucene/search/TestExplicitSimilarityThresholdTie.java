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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnByteVectorField;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * FloatVectorSimilarityQuery.Explicit / ByteVectorSimilarityQuery.Explicit collect every vector
 * whose similarity is {@code >= resultSimilarity}. When traversalSimilarity == resultSimilarity
 * (the 3- and 4-argument constructors), a vector scoring exactly resultSimilarity is dropped once
 * an earlier 64-vector batch of the exhaustive scan has seen a score >= resultSimilarity.
 *
 * <p>Default codec; the segment has fewer vectors than the HNSW graph threshold, so the reader
 * scores every vector (no approximation involved).
 */
@SuppressWarnings("deprecation")
public class TestExplicitSimilarityThresholdTie extends LuceneTestCase {

  private static final String FIELD = "v";
  private static final int NUM_DOCS = 65; // vectors 0..63 form the first scored batch, 64 the second

  /** Two exact copies of the target, at doc 0 and doc 64; EUCLIDEAN similarity 1.0 = identical. */
  public void testFloatExactDuplicates() throws IOException {
    float[] target = {1f, 2f};
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        for (int i = 0; i < NUM_DOCS; i++) {
          float[] v = (i == 0 || i == 64) ? target.clone() : new float[] {100f + i, 100f};
          Document doc = new Document();
          doc.add(new KnnFloatVectorField(FIELD, v, VectorSimilarityFunction.EUCLIDEAN));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query query = new FloatVectorSimilarityQuery.Explicit(FIELD, target, 1.0f);
        assertHits(searcher, query, 0, 64);
      }
    }
  }

  /** Byte vectors at squared distance 1 from the target (similarity 0.5) at doc 0 and doc 64. */
  public void testByteThresholdEqualToScore() throws IOException {
    byte[] target = {10, 10, 10};
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        for (int i = 0; i < NUM_DOCS; i++) {
          byte[] v =
              (i == 0 || i == 64) ? new byte[] {10, 10, 11} : new byte[] {(byte) -i, -100, -100};
          Document doc = new Document();
          doc.add(new KnnByteVectorField(FIELD, v, VectorSimilarityFunction.EUCLIDEAN));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        float threshold = VectorSimilarityFunction.EUCLIDEAN.compare(target, new byte[] {10, 10, 11});
        assertEquals(0.5f, threshold, 0f);
        Query query = new ByteVectorSimilarityQuery.Explicit(FIELD, target, threshold);
        assertHits(searcher, query, 0, 64);
      }
    }
  }

  private static void assertHits(IndexSearcher searcher, Query query, int... expectedDocs)
      throws IOException {
    // explain() of the same query reports both documents as matches ("Score above threshold")
    for (int doc : expectedDocs) {
      assertTrue("explain says doc " + doc + " matches", searcher.explain(query, doc).isMatch());
    }
    TopDocs topDocs = searcher.search(query, 10);
    List<Integer> got = new ArrayList<>();
    for (ScoreDoc sd : topDocs.scoreDocs) {
      got.add(sd.doc);
    }
    got.sort(null);
    List<Integer> expected = new ArrayList<>();
    for (int d : expectedDocs) {
      expected.add(d);
    }
    assertEquals("documents with similarity >= resultSimilarity", expected, got);
    assertEquals(expectedDocs.length, searcher.count(query));
  }
}
