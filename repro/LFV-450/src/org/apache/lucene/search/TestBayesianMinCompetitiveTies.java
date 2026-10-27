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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * BayesianScoreQuery turns the minimum competitive probability into a minimum inner score. In the
 * high-probability range one float probability covers many inner scores, and the threshold it
 * computes lies inside that range, so inner scores whose probability is exactly the minimum
 * competitive score are skipped. Public API only. The inner query is a disjunction of
 * ConstantScoreQuery clauses with boosts, so every inner score is an exact, known float.
 */
public class TestBayesianMinCompetitiveTies extends LuceneTestCase {

  /** {inner score of doc A, inner score of doc B}; B's probability is the next float above A's. */
  private static final float[][] PAIRS = {
    {4.999999f, 5.0f}, // p 0.9933071 vs 0.9933072
    {8.999863f, 9.0f}, // p 0.99987656 vs 0.9998766
    {11.9959545f, 12.0f}, // p 0.9999938 vs 0.99999386
    {15.382769f, 15.5f}, // p 0.99999976 vs 0.9999998
  };

  private static Query constant(String term, float score) {
    return new BoostQuery(new ConstantScoreQuery(new TermQuery(new Term("f", term))), score);
  }

  private static Query bayesian(float innerA, float innerB) {
    Query inner =
        new BooleanQuery.Builder()
            .add(constant("filler", 1f), BooleanClause.Occur.SHOULD)
            .add(constant("a", innerA), BooleanClause.Occur.SHOULD)
            .add(constant("b", innerB), BooleanClause.Occur.SHOULD)
            .build();
    return new BayesianScoreQuery(inner, 1f, 0f); // probability = sigmoid(inner score)
  }

  /** 1,000 filler docs, then doc A (doc id 1000), then doc B (doc id 1001). */
  private static IndexReader index(Directory dir) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 1002; i++) {
        Document doc = new Document();
        doc.add(new StringField("f", i < 1000 ? "filler" : i == 1000 ? "a" : "b", Field.Store.NO));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return DirectoryReader.open(dir);
  }

  /** Default top-1 search must return doc B, whose probability is strictly higher than A's. */
  public void testTopHitWithDefaultSearch() throws IOException {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      List<String> wrong = new ArrayList<>();
      for (float[] pair : PAIRS) {
        Query q = bayesian(pair[0], pair[1]);
        TopDocs complete =
            searcher.search(q, new TopScoreDocCollectorManager(1, null, Integer.MAX_VALUE));
        TopDocs pruned = searcher.search(q, 1); // starts pruning after 1,000 hits
        float pA = searcher.explain(q, 1000).getValue().floatValue();
        float pB = searcher.explain(q, 1001).getValue().floatValue();
        assertTrue(pB > pA);
        assertEquals(1001, complete.scoreDocs[0].doc);
        if (pruned.scoreDocs[0].doc != complete.scoreDocs[0].doc) {
          wrong.add(
              "inner A="
                  + pair[0]
                  + " (p="
                  + pA
                  + "), inner B="
                  + pair[1]
                  + " (p="
                  + pB
                  + "): search(q, 1) returned doc "
                  + pruned.scoreDocs[0].doc
                  + " score "
                  + pruned.scoreDocs[0].score);
        }
      }
      assertTrue(String.join("\n", wrong), wrong.isEmpty());
    }
  }

  /**
   * Scorable#setMinCompetitiveScore: a scorer may skip only documents whose score is less than
   * minScore. Setting minScore to doc B's own probability must not skip doc B.
   */
  public void testSetMinCompetitiveScoreKeepsTiedDoc() throws IOException {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      List<String> wrong = new ArrayList<>();
      for (float[] pair : PAIRS) {
        Query q = searcher.rewrite(bayesian(pair[0], pair[1]));
        float pB = searcher.explain(q, 1001).getValue().floatValue();
        Weight weight = searcher.createWeight(q, ScoreMode.TOP_SCORES, 1f);
        ScorerSupplier supplier = weight.scorerSupplier(reader.leaves().get(0));
        supplier.setTopLevelScoringClause();
        Scorer scorer = supplier.get(Long.MAX_VALUE);
        scorer.setMinCompetitiveScore(pB);
        boolean found = false;
        DocIdSetIterator it = scorer.iterator();
        for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
          if (doc == 1001) {
            assertEquals(pB, scorer.score(), 0f);
            found = true;
          }
        }
        if (found == false) {
          wrong.add("inner " + pair[1] + ", p=" + pB + ": skipped after setMinCompetitiveScore(p)");
        }
      }
      assertTrue(String.join("\n", wrong), wrong.isEmpty());
    }
  }
}
