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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A values source that needs scores (here DoubleValuesSource.SCORES) must see the inner query's
 * scores. RescoreTopNQuery creates the inner Weight with ScoreMode.COMPLETE_NO_SCORES, whose
 * scorers do not provide scores, so the source reads 0 for every document. A kNN inner query
 * (rewritten to DocAndScoreQuery, which ignores the score mode) is affected once its non-scoring
 * Weight is served from a query cache.
 */
public class TestRescoreTopNQueryInnerScores extends LuceneTestCase {

  public void testScoresSourceSeesInnerQueryScores() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        // docs 0..8 contain "common", doc 9 contains "rare"
        for (int i = 0; i < 10; i++) {
          Document doc = new Document();
          doc.add(new TextField("body", i < 9 ? "common" : "rare", Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null); // this case does not depend on query caching
        Query inner =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("body", "common")), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("body", "rare")), BooleanClause.Occur.SHOULD)
                .build();

        TopDocs plain = searcher.search(inner, 1);
        assertEquals(9, plain.scoreDocs[0].doc); // the rare term scores highest
        assertTrue(plain.scoreDocs[0].score > 0);

        // Rescoring by the inner query's own score must keep the same best hit and score.
        TopDocs rescored =
            searcher.search(new RescoreTopNQuery(inner, DoubleValuesSource.SCORES, 1), 1);
        assertEquals("best hit", 9, rescored.scoreDocs[0].doc);
        assertEquals("rescored score", plain.scoreDocs[0].score, rescored.scoreDocs[0].score, 0f);
      }
    }
  }

  /**
   * kNN inner query, searched repeatedly with the query cache settings that are IndexSearcher's
   * defaults in Lucene 10.x (main disabled the default cache in #14187).
   */
  public void testKnnInnerQueryWithQueryCache() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 12000; i++) {
          Document doc = new Document();
          doc.add(
              new KnnFloatVectorField(
                  "vector", new float[] {1, i}, VectorSimilarityFunction.EUCLIDEAN));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(new LRUQueryCache(1000, 1L << 25));
        searcher.setQueryCachingPolicy(new UsageTrackingQueryCachingPolicy());
        Query knn = new KnnFloatVectorQuery("vector", new float[] {1, 11999}, 10);
        Query rescore = new RescoreTopNQuery(knn, DoubleValuesSource.SCORES, 3);
        for (int run = 1; run <= 6; run++) {
          ScoreDoc[] hits = searcher.search(rescore, 3).scoreDocs;
          assertEquals("run " + run, 11999, hits[0].doc);
          assertEquals("run " + run, 11998, hits[1].doc);
          assertEquals("run " + run, 11997, hits[2].doc);
          assertTrue("run " + run + " score " + hits[0].score, hits[0].score > 0);
        }
      }
    }
  }
}
