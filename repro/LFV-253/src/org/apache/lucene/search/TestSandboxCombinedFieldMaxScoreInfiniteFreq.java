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
import org.apache.lucene.sandbox.search.CombinedFieldQuery;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.AxiomaticF2LOG;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * CombinedFieldQuery's scorer reports {@code simScorer.score(Float.POSITIVE_INFINITY, 1L)} as its
 * maximum score. SimScorer#score requires a finite freq and documents score(Float.MAX_VALUE, 1) as
 * the bound. AxiomaticF2LOG (a core similarity that keeps the default computeNorm, so it is
 * compatible with CombinedFieldQuery per its javadoc) returns NaN for an infinite freq, so the
 * scorer's max score is NaN and top-k pruning drops competitive hits.
 */
public class TestSandboxCombinedFieldMaxScoreInfiniteFreq extends LuceneTestCase {

  private static Directory buildIndex(Similarity sim) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setSimilarity(sim);
    iwc.setMergePolicy(newLogMergePolicy());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      // doc 0: matches both clauses
      Document d0 = new Document();
      d0.add(new TextField("a", "x", Field.Store.NO));
      d0.add(new TextField("c", "y", Field.Store.NO));
      w.addDocument(d0);
      // docs 1..299: match only the (common, low-idf) TermQuery clause
      for (int i = 1; i < 300; i++) {
        Document d = new Document();
        d.add(new TextField("c", "y", Field.Store.NO));
        w.addDocument(d);
      }
      // doc 300: matches only the CombinedFieldQuery, and has the best score
      Document d300 = new Document();
      d300.add(new TextField("a", "x x x", Field.Store.NO));
      w.addDocument(d300);
      w.forceMerge(1);
    }
    return dir;
  }

  private static Query combined() {
    return new CombinedFieldQuery.Builder().addField("a").addField("b").addTerm(new org.apache.lucene.util.BytesRef("x")).build();
  }

  /** Scorer#getMaxScore must be an upper bound of every score the scorer returns. */
  public void testMaxScoreIsAnUpperBound() throws Exception {
    Similarity sim = new AxiomaticF2LOG();
    try (Directory dir = buildIndex(sim);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setSimilarity(sim);
      searcher.setQueryCache(null);
      Weight weight =
          searcher.createWeight(searcher.rewrite(combined()), ScoreMode.TOP_SCORES, 1f);
      LeafReaderContext ctx = reader.leaves().get(0);
      Scorer scorer = weight.scorer(ctx);
      assertNotNull(scorer);
      float maxScore = scorer.getMaxScore(DocIdSetIterator.NO_MORE_DOCS);
      for (int doc = scorer.iterator().nextDoc();
          doc != DocIdSetIterator.NO_MORE_DOCS;
          doc = scorer.iterator().nextDoc()) {
        float score = scorer.score();
        assertTrue(
            "doc " + doc + " scores " + score + " but getMaxScore returned " + maxScore,
            score <= maxScore);
      }
    }
  }

  /** A top-1 search must return the same hit with and without dynamic pruning. */
  public void testTopHitWithPruning() throws Exception {
    Similarity sim = new AxiomaticF2LOG();
    try (Directory dir = buildIndex(sim);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setSimilarity(sim);
      searcher.setQueryCache(null);
      Query query =
          new BooleanQuery.Builder()
              .add(combined(), BooleanClause.Occur.SHOULD)
              .add(new TermQuery(new Term("c", "y")), BooleanClause.Occur.SHOULD)
              .build();
      // exhaustive (ScoreMode.COMPLETE)
      TopDocs exact = searcher.search(query, new TopScoreDocCollectorManager(1, Integer.MAX_VALUE));
      // dynamic pruning (ScoreMode.TOP_SCORES once 1 hit has been collected)
      TopDocs pruned = searcher.search(query, new TopScoreDocCollectorManager(1, 1));
      assertEquals("exhaustive top hit", 300, exact.scoreDocs[0].doc);
      assertEquals(
          "pruned search returned doc "
              + pruned.scoreDocs[0].doc
              + " (score "
              + pruned.scoreDocs[0].score
              + ") instead of doc 300 (score "
              + exact.scoreDocs[0].score
              + ")",
          exact.scoreDocs[0].doc,
          pruned.scoreDocs[0].doc);
    }
  }
}
