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
import java.util.Random;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.hnsw.HnswGraphProvider;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnByteVectorField;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.hnsw.HnswGraph;

/**
 * FloatVectorSimilarityQuery.Explicit / ByteVectorSimilarityQuery.Explicit collect every vector
 * whose similarity is {@code >= resultSimilarity}. When traversalSimilarity == resultSimilarity
 * (the 3- and 4-argument constructors), a vector scoring exactly resultSimilarity is dropped once
 * another vector at or above resultSimilarity has been collected in the same segment.
 *
 * <p>The first two tests use a segment with fewer vectors than the HNSW graph threshold, so the
 * reader scores every vector (no approximation involved): the tie is lost at the boundary of the
 * 64-vector scoring batches. The third test uses a segment with an HNSW graph: lowering
 * traversalSimilarity by one ulp finds every copy, so the loss is the tie handling, not recall.
 */
@SuppressWarnings("deprecation")
public class TestExplicitSimilarityThresholdTie extends LuceneTestCase {

  private static final String FIELD = "v";
  // vectors 0..63 form the first scored batch, vector 64 the second
  private static final int NUM_DOCS = 65;

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
        assertHits(searcher, "traversal == result", query, 0, 64);
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
        float threshold =
            VectorSimilarityFunction.EUCLIDEAN.compare(target, new byte[] {10, 10, 11});
        assertEquals(0.5f, threshold, 0f);
        Query query = new ByteVectorSimilarityQuery.Explicit(FIELD, target, threshold);
        assertHits(searcher, "traversal == result", query, 0, 64);
      }
    }
  }

  /** 3,000 vectors (the segment has an HNSW graph) with 6 exact copies of the target. */
  public void testFloatExactDuplicatesOnGraph() throws IOException {
    float[] target = {0.25f, -0.5f, 0.75f, 1f};
    int numDocs = 3000;
    int[] copies = {7, 500, 1200, 1999, 2500, 2999};
    Random r = new Random(42);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        for (int i = 0, c = 0; i < numDocs; i++) {
          float[] v;
          if (c < copies.length && i == copies[c]) {
            v = target.clone();
            c++;
          } else {
            v = new float[target.length];
            for (int d = 0; d < v.length; d++) {
              v[d] = r.nextFloat() * 4 - 2;
            }
          }
          Document doc = new Document();
          doc.add(new KnnFloatVectorField(FIELD, v, VectorSimilarityFunction.EUCLIDEAN));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        // precondition: the segment has an HNSW graph, so this is the graph search path
        KnnVectorsReader vectorsReader =
            ((CodecReader) reader.leaves().get(0).reader()).getVectorReader();
        assertTrue(vectorsReader instanceof HnswGraphProvider);
        HnswGraph graph = ((HnswGraphProvider) vectorsReader).getGraph(FIELD);
        assertTrue("segment has an HNSW graph", graph != null && graph.size() == numDocs);

        IndexSearcher searcher = new IndexSearcher(reader);
        // control: the same threshold with a traversal one ulp lower finds every copy
        Query control =
            new FloatVectorSimilarityQuery.Explicit(FIELD, target, Math.nextDown(1f), 1f);
        assertHits(searcher, "control, traversal one ulp lower", control, copies);
        Query query = new FloatVectorSimilarityQuery.Explicit(FIELD, target, 1.0f);
        assertHits(searcher, "traversal == result", query, copies);
      }
    }
  }

  private static void assertHits(
      IndexSearcher searcher, String what, Query query, int... expectedDocs) throws IOException {
    // explain() of the same query reports every expected document as a match ("Score above
    // threshold")
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
    assertEquals(what + ": documents with similarity >= resultSimilarity", expected, got);
    assertEquals(what + ": count()", expectedDocs.length, searcher.count(query));
  }
}
