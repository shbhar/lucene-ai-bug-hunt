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
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * LogOddsFusionQuery with per-signal logit bounds (logitMin/logitMax) and no weights: every clause
 * should be normalized with its own bounds, as the constructor javadoc ("per-signal logit lower
 * bounds") and explain() say. Uses only public API. Each clause is a ConstantScoreQuery with a
 * boost p, so it scores exactly p (no similarity or index statistics involved).
 */
public class TestLogOddsFusionUnweightedLogitBounds extends LuceneTestCase {

  private static Query clause(String term, float probability) {
    return new BoostQuery(new ConstantScoreQuery(new TermQuery(new Term("f", term))), probability);
  }

  /** The documented formula for uniform weighting with logit normalization. */
  private static double documentedScore(
      int numClauses, float alpha, float[] probs, float[] logitMin, float[] logitMax) {
    double sum = 0;
    for (int i = 0; i < probs.length; i++) {
      if (Float.isNaN(probs[i])) {
        continue; // clause does not match: contributes 0
      }
      double logit = Math.log(probs[i] / (1.0 - probs[i]));
      double gated = (logit - logitMin[i]) / (logitMax[i] - logitMin[i]);
      sum += Math.min(1, Math.max(0, gated));
    }
    double scaled = sum / numClauses * Math.pow(numClauses, alpha);
    return 1 / (1 + Math.exp(-scaled));
  }

  private static IndexReader index(Directory dir, String... docs) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String terms : docs) {
        Document doc = new Document();
        for (String t : terms.split(" ")) {
          doc.add(new StringField("f", t, Field.Store.NO));
        }
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return DirectoryReader.open(dir);
  }

  /** Doc 1 matches only the second clause, which is normalized with the first clause's bounds. */
  public void testSecondClauseUsesItsOwnBounds() throws IOException {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir, "a", "b")) {
      IndexSearcher searcher = newSearcher(reader, false);
      float[] logitMin = {0f, -1f};
      float[] logitMax = {100f, 1f};
      Query q =
          new LogOddsFusionQuery(
              List.of(clause("a", 0.9f), clause("b", 0.6f)), 0.5f, null, logitMin, logitMax);

      TopDocs td = searcher.search(q, 2);
      assertEquals(2, td.scoreDocs.length);
      float scoreA = td.scoreDocs[0].doc == 0 ? td.scoreDocs[0].score : td.scoreDocs[1].score;
      float scoreB = td.scoreDocs[0].doc == 1 ? td.scoreDocs[0].score : td.scoreDocs[1].score;
      double expectedA =
          documentedScore(2, 0.5f, new float[] {0.9f, Float.NaN}, logitMin, logitMax);
      double expectedB =
          documentedScore(2, 0.5f, new float[] {Float.NaN, 0.6f}, logitMin, logitMax);
      // explain() applies each clause's own bounds
      assertEquals(
          "explain() of doc 1", expectedB, searcher.explain(q, 1).getValue().floatValue(), 1e-6);
      assertEquals("score of doc 0 (first clause)", expectedA, scoreA, 1e-6);
      // fails: doc 1 is scored with clause 0's bounds (0, 100) instead of its own (-1, 1)
      assertEquals("score of doc 1 (second clause)", expectedB, scoreB, 1e-6);
      assertEquals("doc 1 must rank first", 1, td.scoreDocs[0].doc);
    }
  }

  /** The same mix-up makes score() exceed the scorer's own getMaxScore(). */
  public void testScoreDoesNotExceedMaxScore() throws IOException {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir, "a b")) {
      IndexSearcher searcher = newSearcher(reader, false);
      Query q =
          new LogOddsFusionQuery(
              List.of(clause("a", 0.9f), clause("b", 0.6f)),
              0.5f,
              null,
              new float[] {-1f, 0f},
              new float[] {1f, 100f});
      Weight weight = searcher.createWeight(searcher.rewrite(q), ScoreMode.TOP_SCORES, 1f);
      LeafReaderContext leaf = reader.leaves().get(0);
      Scorer scorer = weight.scorer(leaf);
      assertEquals(0, scorer.iterator().nextDoc());
      float maxScore = scorer.getMaxScore(DocIdSetIterator.NO_MORE_DOCS);
      float score = scorer.score();
      assertTrue("score " + score + " > getMaxScore " + maxScore, score <= maxScore);
    }
  }

  /**
   * As a clause of a disjunction, the too-low bound lets pruning drop the hit that scores best.
   * 5,000 docs match only "c" (score 0.7) and the last doc matches the fusion query only.
   */
  public void testTopScoresMatchesComplete() throws IOException {
    String[] docs = new String[5001];
    java.util.Arrays.fill(docs, "c");
    docs[5000] = "a b";
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir, docs)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      Query fusion =
          new LogOddsFusionQuery(
              List.of(clause("a", 0.9f), clause("b", 0.6f)),
              0.5f,
              null,
              new float[] {-1f, 0f},
              new float[] {1f, 100f});
      Query q =
          new BooleanQuery.Builder()
              .add(fusion, BooleanClause.Occur.SHOULD)
              .add(clause("c", 0.7f), BooleanClause.Occur.SHOULD)
              .build();
      TopDocs complete =
          searcher.search(q, new TopScoreDocCollectorManager(1, null, Integer.MAX_VALUE));
      TopDocs pruned = searcher.search(q, 1); // default top-k search (prunes after 1,000 hits)
      // on main, COMPLETE returns doc 5000 (0.76923805) and the pruned search doc 0 (0.7)
      assertEquals(complete.scoreDocs[0].doc, pruned.scoreDocs[0].doc);
      assertEquals(complete.scoreDocs[0].score, pruned.scoreDocs[0].score, 0f);
    }
  }
}
