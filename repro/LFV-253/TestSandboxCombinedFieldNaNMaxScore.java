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
package org.apache.lucene.sandbox.search;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.search.similarities.AxiomaticF2EXP;
import org.apache.lucene.search.similarities.AxiomaticF2LOG;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Independent check of two contracts for CombinedFieldQuery with AxiomaticF2LOG / AxiomaticF2EXP
 * (similarities that keep the default computeNorm, which the CombinedFieldQuery javadoc names as
 * the compatibility condition):
 *
 * <ul>
 *   <li>Scorer#getMaxScore(upTo) bounds every score the scorer returns up to upTo.
 *   <li>A top-1 search with dynamic pruning returns the same hit as an exhaustive top-1 search.
 * </ul>
 *
 * Only public API is used. Variant for releases where CombinedFieldQuery lives in the sandbox
 * module (9.x, 10.0, 10.1) and its builder takes terms through addTerm.
 */
public class TestSandboxCombinedFieldNaNMaxScore extends LuceneTestCase {

  private static final int NUM_DOCS = 3000;
  private static final int WEAK_DOC = 0;
  private static final int BEST_DOC = 2000;

  private static void indexDocs(Directory dir, Similarity sim) throws IOException {
    IndexWriterConfig iwc =
        new IndexWriterConfig(new MockAnalyzer(random()))
            .setSimilarity(sim)
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(newLogMergePolicy());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < NUM_DOCS; i++) {
        Document doc = new Document();
        if (i == WEAK_DOC) {
          // matches only the combined-field query; one occurrence in a long field
          doc.add(new TextField("a", "x f f f f f f f f f f f f f f f f f f f", Field.Store.NO));
          doc.add(new TextField("b", "f f f f f", Field.Store.NO));
        } else if (i == BEST_DOC) {
          // matches only the combined-field query; several occurrences in short fields
          doc.add(new TextField("a", "x x x", Field.Store.NO));
          doc.add(new TextField("b", "x", Field.Store.NO));
        } else {
          // matches only the low-idf term query c:y
          doc.add(new TextField("a", "f f", Field.Store.NO));
          doc.add(new TextField("b", "f", Field.Store.NO));
          doc.add(new TextField("c", "y", Field.Store.NO));
        }
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
  }

  private static Query combinedField() {
    return new CombinedFieldQuery.Builder().addField("a").addField("b").addTerm(new BytesRef("x")).build();
  }

  private static Query disjunction() {
    return new BooleanQuery.Builder()
        .add(combinedField(), BooleanClause.Occur.SHOULD)
        .add(new TermQuery(new Term("c", "y")), BooleanClause.Occur.SHOULD)
        .build();
  }

  private static IndexSearcher searcher(DirectoryReader reader, Similarity sim) {
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(sim);
    searcher.setQueryCache(null);
    return searcher;
  }

  static void checkMaxScoreIsUpperBound(Similarity sim) throws IOException {
    try (Directory dir = newDirectory()) {
      indexDocs(dir, sim);
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        IndexSearcher searcher = searcher(reader, sim);
        Query q = searcher.rewrite(combinedField());
        Weight weight = searcher.createWeight(q, ScoreMode.TOP_SCORES, 1f);
        LeafReaderContext ctx = reader.leaves().get(0);
        Scorer scorer = weight.scorer(ctx);
        assertNotNull(scorer);
        scorer.advanceShallow(0);
        float maxScore = scorer.getMaxScore(DocIdSetIterator.NO_MORE_DOCS);
        int matches = 0;
        DocIdSetIterator it = scorer.iterator();
        for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
          float score = scorer.score();
          matches++;
          assertFalse("score of doc " + doc + " is NaN", Float.isNaN(score));
          assertTrue(
              sim + ": doc " + doc + " scores " + score + " but getMaxScore returned " + maxScore,
              score <= maxScore);
        }
        assertEquals(2, matches);
      }
    }
  }

  static void checkPrunedTopHit(Similarity sim) throws IOException {
    try (Directory dir = newDirectory()) {
      indexDocs(dir, sim);
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        IndexSearcher searcher = searcher(reader, sim);
        Query q = disjunction();

        TopDocs exhaustive =
            searcher.search(q, new TopScoreDocCollectorManager(1, null, Integer.MAX_VALUE));
        // sanity on the data, independent of pruning
        assertEquals(NUM_DOCS, searcher.count(q));
        assertEquals(
            sim + ": exhaustive top hit", BEST_DOC, exhaustive.scoreDocs[0].doc);

        TopDocs pruned = searcher.search(q, new TopScoreDocCollectorManager(1, null, 1));
        assertEquals(
            sim
                + ": pruned top-1 returned doc "
                + pruned.scoreDocs[0].doc
                + " (score "
                + pruned.scoreDocs[0].score
                + ") but the exhaustive top-1 is doc "
                + exhaustive.scoreDocs[0].doc
                + " (score "
                + exhaustive.scoreDocs[0].score
                + ")",
            exhaustive.scoreDocs[0].doc,
            pruned.scoreDocs[0].doc);
      }
    }
  }

  public void testCheckHitsTopScoresF2LOG() throws IOException {
    // the upstream oracle used by Lucene's own query tests
    Similarity sim = new AxiomaticF2LOG();
    try (Directory dir = newDirectory()) {
      indexDocs(dir, sim);
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        org.apache.lucene.tests.search.CheckHits.checkTopScores(
            random(), disjunction(), searcher(reader, sim));
      }
    }
  }

  public void testMaxScoreUpperBoundF2LOG() throws IOException {
    checkMaxScoreIsUpperBound(new AxiomaticF2LOG());
  }

  public void testMaxScoreUpperBoundF2EXP() throws IOException {
    checkMaxScoreIsUpperBound(new AxiomaticF2EXP());
  }

  public void testPrunedTopHitF2LOG() throws IOException {
    checkPrunedTopHit(new AxiomaticF2LOG());
  }

  public void testPrunedTopHitF2EXP() throws IOException {
    checkPrunedTopHit(new AxiomaticF2EXP());
  }
}
