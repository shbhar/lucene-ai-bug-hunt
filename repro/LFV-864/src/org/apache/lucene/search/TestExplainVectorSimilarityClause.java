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
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
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
 * IndexSearcher#explain of a BooleanQuery must agree with the BooleanQuery's hits: a document the
 * BooleanQuery does not return must not be explained as a match with some score. With a
 * FloatVectorSimilarityQuery clause (approximate graph search for hits, exact similarity in its own
 * explain) BooleanWeight#explain trusts the clause explanations, pulls its own scorer, advances it
 * to doc without checking that it lands on doc, and reports the score of the next hit.
 */
public class TestExplainVectorSimilarityClause extends LuceneTestCase {

  public void testExplainOfNonHitIsNotAMatch() throws IOException {
    Random r = new Random(42); // fixed data, independent of the test seed
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 3000; i++) {
          Document d = new Document();
          d.add(
              new KnnFloatVectorField(
                  "vec",
                  new float[] {r.nextInt(5), r.nextInt(5), 1 + r.nextInt(4)},
                  VectorSimilarityFunction.EUCLIDEAN));
          d.add(new StringField("tag", i % 2 == 0 ? "even" : "odd", Field.Store.NO));
          w.addDocument(d);
        }
        w.forceMerge(1);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null); // the production default on main
        int checked = 0;
        for (int t = 0; t < 20; t++) {
          float[] target = {r.nextInt(5), r.nextInt(5), 1 + r.nextInt(4)};
          Query vq = new FloatVectorSimilarityQuery.Adaptive("vec", target, 0.05f);
          Query bq =
              new BooleanQuery.Builder()
                  .add(new TermQuery(new Term("tag", "even")), BooleanClause.Occur.MUST)
                  .add(vq, BooleanClause.Occur.MUST)
                  .build();
          Map<Integer, Float> hits = new HashMap<>();
          for (ScoreDoc sd :
              searcher.search(bq, new TopScoreDocCollectorManager(reader.maxDoc(), Integer.MAX_VALUE))
                  .scoreDocs) {
            hits.put(sd.doc, sd.score);
          }
          for (int doc = 0; doc < reader.maxDoc(); doc += 2) {
            if (hits.containsKey(doc)) continue;
            checked++;
            Explanation e = searcher.explain(bq, doc);
            int nextHit = Integer.MAX_VALUE;
            for (int h : hits.keySet()) if (h > doc && h < nextHit) nextHit = h;
            assertFalse(
                "doc "
                    + doc
                    + " is not a hit of "
                    + bq
                    + " but is explained as a match with score "
                    + e.getValue()
                    + "; the next hit is doc "
                    + nextHit
                    + " with score "
                    + hits.get(nextHit)
                    + "; explanation: "
                    + e,
                e.isMatch());
          }
        }
        assertTrue(checked > 0);
      }
    }
  }

  /**
   * Three segments: in the last one the approximate search of the vector clause collects nothing,
   * so the BooleanQuery has no scorer for that leaf, while the clause explanations of doc 12012
   * all say "match": IndexSearcher#explain throws NullPointerException (BooleanWeight.java:127).
   */
  public void testExplainWhenTheLeafHasNoScorer() throws IOException {
    Random r = new Random(42); // fixed data, independent of the test seed
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setCodec(TestUtil.getDefaultCodec());
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
      iwc.setMaxBufferedDocs(6000);
      iwc.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 18000; i++) {
          Document d = new Document();
          d.add(
              new KnnFloatVectorField(
                  "vec",
                  new float[] {r.nextInt(5), r.nextInt(5), 1 + r.nextInt(4)},
                  VectorSimilarityFunction.EUCLIDEAN));
          d.add(new StringField("tag", i % 2 == 0 ? "even" : "odd", Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        assertEquals(3, reader.leaves().size());
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null); // the production default on main
        Query vq = new FloatVectorSimilarityQuery.Adaptive("vec", new float[] {0, 3, 4}, 0.5f);
        Query bq =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("tag", "even")), BooleanClause.Occur.MUST)
                .add(vq, BooleanClause.Occur.MUST)
                .build();
        TopDocs hits =
            searcher.search(bq, new TopScoreDocCollectorManager(reader.maxDoc(), Integer.MAX_VALUE));
        for (ScoreDoc sd : hits.scoreDocs) assertNotEquals(12012, sd.doc);
        // On main the vector clause alone explains doc 12012 as a match (exact similarity >= 0.5),
        // although the approximate search of its segment collected nothing.
        Explanation e = searcher.explain(bq, 12012); // throws NullPointerException on main
        assertFalse("doc 12012 is not a hit but is explained as a match: " + e, e.isMatch());
      }
    }
  }

  /** The same segment layout, with the vector query wrapped in a FunctionScoreQuery. */
  public void testFunctionScoreQueryExplainWhenTheLeafHasNoScorer() throws IOException {
    Random r = new Random(42); // the same fixed data as testExplainWhenTheLeafHasNoScorer
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setCodec(TestUtil.getDefaultCodec());
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
      iwc.setMaxBufferedDocs(6000);
      iwc.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 18000; i++) {
          Document d = new Document();
          d.add(
              new KnnFloatVectorField(
                  "vec",
                  new float[] {r.nextInt(5), r.nextInt(5), 1 + r.nextInt(4)},
                  VectorSimilarityFunction.EUCLIDEAN));
          d.add(new StringField("tag", i % 2 == 0 ? "even" : "odd", Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        Query vq = new FloatVectorSimilarityQuery.Adaptive("vec", new float[] {0, 3, 4}, 0.5f);
        Query fq =
            new org.apache.lucene.queries.function.FunctionScoreQuery(vq, DoubleValuesSource.SCORES);
        for (ScoreDoc sd : searcher.search(fq, reader.maxDoc()).scoreDocs) {
          assertNotEquals(12012, sd.doc);
        }
        Explanation e = searcher.explain(fq, 12012);
        assertFalse("doc 12012 is not a hit but is explained as a match: " + e, e.isMatch());
      }
    }
  }

  /**
   * The vector-similarity query alone: a document above the threshold that the approximate graph
   * search did not collect is not a hit, so its explanation must not be a match (kNN queries report
   * such an "approximate-search miss" as a non-match, see DocAndScoreQuery's no-match explainer).
   */
  public void testVectorSimilarityQueryExplainOfApproximateMiss() throws IOException {
    Random r = new Random(42); // the same fixed data as testExplainOfNonHitIsNotAMatch
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 3000; i++) {
          Document d = new Document();
          d.add(
              new KnnFloatVectorField(
                  "vec",
                  new float[] {r.nextInt(5), r.nextInt(5), 1 + r.nextInt(4)},
                  VectorSimilarityFunction.EUCLIDEAN));
          w.addDocument(d);
        }
        w.forceMerge(1);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        float[] target = {r.nextInt(5), r.nextInt(5), 1 + r.nextInt(4)};
        Query vq = new FloatVectorSimilarityQuery.Adaptive("vec", target, 0.05f);
        Map<Integer, Float> hits = new HashMap<>();
        for (ScoreDoc sd :
            searcher.search(vq, new TopScoreDocCollectorManager(reader.maxDoc(), Integer.MAX_VALUE))
                .scoreDocs) {
          hits.put(sd.doc, sd.score);
        }
        int misses = 0;
        for (int doc = 0; doc < reader.maxDoc(); doc++) {
          if (hits.containsKey(doc) == false && searcher.explain(vq, doc).isMatch()) misses++;
        }
        assertEquals(
            "documents explained as matches that " + vq + " does not return (of " + hits.size() + " hits)",
            0,
            misses);
      }
    }
  }
}
