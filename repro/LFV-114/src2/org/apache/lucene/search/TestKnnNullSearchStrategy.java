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

import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * KnnFloatVectorQuery documents that a null search strategy means "the default strategy will be
 * used". With a null strategy the query must therefore return the same hits as with the default
 * strategy. It throws NullPointerException instead whenever a seeded HNSW search runs: in the
 * optimistic re-entry pass of a multi-segment search, and for SeededKnnVectorQuery.
 */
public class TestKnnNullSearchStrategy extends LuceneTestCase {

  /**
   * segs segments of perSeg docs, default codec, no merges; doc x has vector (x, 1), so segment 0
   * holds all of the query's nearest neighbours. Segments are large enough to get an HNSW graph.
   */
  private static Directory index(int segs, int perSeg) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      int x = 0;
      for (int s = 0; s < segs; s++) {
        for (int i = 0; i < perSeg; i++, x++) {
          Document d = new Document();
          d.add(new StringField("tag", x % 2 == 0 ? "even" : "odd", Field.Store.NO));
          d.add(new KnnFloatVectorField("v", new float[] {x, 1}, VectorSimilarityFunction.EUCLIDEAN));
          w.addDocument(d);
        }
        w.commit();
      }
    }
    return dir;
  }

  private static int[] docs(TopDocs td) {
    return Arrays.stream(td.scoreDocs).mapToInt(sd -> sd.doc).toArray();
  }

  /** Plain multi-segment kNN search: the optimistic second pass seeds HNSW with the first-pass hits. */
  public void testNullStrategyMultiSegment() throws Exception {
    try (Directory dir = index(10, 2000);
        IndexReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      float[] target = {0, 1};
      int k = 50;
      // (release variant: DocAndScoreQuery#reentryCount is main-only, so the re-entry control is omitted)
      TopDocs expected = searcher.search(new KnnFloatVectorQuery("v", target, k, null), k);
      // a null strategy is documented to mean the default strategy
      TopDocs actual = searcher.search(new KnnFloatVectorQuery("v", target, k, null, null), k);
      assertArrayEquals(docs(expected), docs(actual));
    }
  }

  /** SeededKnnVectorQuery wrapping a query with a null strategy. */
  public void testNullStrategySeeded() throws Exception {
    try (Directory dir = index(1, 2000);
        IndexReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      float[] target = {0, 1};
      Query seed = new TermQuery(new Term("tag", "even"));
      TopDocs expected =
          searcher.search(
              SeededKnnVectorQuery.fromFloatQuery(new KnnFloatVectorQuery("v", target, 3), seed), 3);
      TopDocs actual =
          searcher.search(
              SeededKnnVectorQuery.fromFloatQuery(
                  new KnnFloatVectorQuery("v", target, 3, null, null), seed),
              3);
      assertArrayEquals(docs(expected), docs(actual));
    }
  }
}
