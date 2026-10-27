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
package org.apache.lucene.queries.function;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * FunctionScoreQuery's block-max bound (#16431) multiplies the value source's bound by the max
 * score of the wrapped query. When the source does not need scores, the wrapped query is weighted
 * with COMPLETE_NO_SCORES, and e.g. a TermQuery weighted that way reports a max score of 0, so the
 * bound becomes 0 although the documents score up to the largest field value.
 */
public class TestFunctionScoreQueryNonScoringInnerMaxScore extends LuceneTestCase {

  // more than one 4096-doc scoring window, so that dynamic pruning kicks in before the best doc
  private static final int NUM_DOCS = 5_000;

  private static Directory indexDocs() throws Exception {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < NUM_DOCS; i++) {
        Document doc = new Document();
        doc.add(new StringField("body", "a", Field.Store.NO));
        if (i == 0) {
          doc.add(new StringField("body", "b", Field.Store.NO));
        }
        // a numeric doc-values field with a skip index, the input #16431 uses for block-max bounds;
        // the last document is the most popular one
        long popularity = i == NUM_DOCS - 1 ? 100 : 1;
        doc.add(NumericDocValuesField.indexedField("popularity", popularity));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  private static Query functionScore() {
    return new FunctionScoreQuery(
        new TermQuery(new Term("body", "a")), DoubleValuesSource.fromLongField("popularity"));
  }

  /** TOP_SCORES must return the same top hit as COMPLETE. */
  public void testTopHitWithDynamicPruning() throws Exception {
    try (Directory dir = indexDocs();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      Query query =
          new BooleanQuery.Builder()
              .add(functionScore(), Occur.SHOULD)
              .add(new TermQuery(new Term("body", "b")), Occur.SHOULD)
              .build();
      TopDocs complete =
          searcher.search(query, new TopScoreDocCollectorManager(1, null, Integer.MAX_VALUE));
      TopDocs topScores = searcher.search(query, new TopScoreDocCollectorManager(1, null, 1));
      // the last doc (popularity 100) is the best hit
      assertEquals(NUM_DOCS - 1, complete.scoreDocs[0].doc);
      assertEquals(
          "TOP_SCORES top hit (score " + topScores.scoreDocs[0].score + ") differs from COMPLETE",
          complete.scoreDocs[0].doc,
          topScores.scoreDocs[0].doc);
    }
  }

  /**
   * TestFunctionScoreQuery#testMaxScoreDelegationWithDocValuesSkipper with two changes: the field
   * gets a skip index (upstream it has none, so the branch that checks the bound never runs), and
   * upTo is the last doc ID instead of maxDoc (since #16697 a range that extends past the skipper's
   * last interval gets an infinite bound).
   */
  public void testUpstreamDelegationTestWithSkipIndex() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig conf = newIndexWriterConfig();
      try (IndexWriter indexWriter = new IndexWriter(dir, conf)) {
        for (int i = 1; i <= 500; i++) {
          Document doc = new Document();
          doc.add(new org.apache.lucene.document.TextField("text", "test doc", Field.Store.NO));
          doc.add(NumericDocValuesField.indexedField("val", i * 10)); // upstream: new NumericDocValuesField
          indexWriter.addDocument(doc);
        }
        indexWriter.commit();
      }

      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        DoubleValuesSource valueSource = DoubleValuesSource.fromLongField("val");
        Query q = new FunctionScoreQuery(new TermQuery(new Term("text", "test")), valueSource);

        org.apache.lucene.search.Weight weight = searcher.createWeight(q, ScoreMode.TOP_SCORES, 1f);
        LeafReaderContext ctx = reader.leaves().get(0);
        var scorerSupplier = weight.scorerSupplier(ctx);
        assertNotNull(scorerSupplier);
        var scorer = scorerSupplier.get(Long.MAX_VALUE);
        assertNotNull(scorer);

        int maxDoc = ctx.reader().maxDoc();
        int shallowEnd = scorer.advanceShallow(0);
        assertTrue(shallowEnd >= 0);

        float maxScore = scorer.getMaxScore(maxDoc - 1); // upstream: getMaxScore(maxDoc)
        assertNotNull(ctx.reader().getDocValuesSkipper("val"));
        assertFalse(Float.isInfinite(maxScore));
        assertTrue("maxScore=" + maxScore, maxScore >= 5000f);
      }
    }
  }

  /** Scorer#getMaxScore must be an upper bound of every score up to upTo. */
  public void testMaxScoreIsUpperBound() throws Exception {
    try (Directory dir = indexDocs();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      LeafReaderContext leaf = reader.leaves().get(0);
      Scorer scorer =
          searcher
              .createWeight(searcher.rewrite(functionScore()), ScoreMode.TOP_SCORES, 1f)
              .scorer(leaf);
      int upTo = scorer.advanceShallow(0);
      float maxScore = scorer.getMaxScore(upTo);
      for (int doc = scorer.iterator().nextDoc();
          doc != DocIdSetIterator.NO_MORE_DOCS && doc <= upTo;
          doc = scorer.iterator().nextDoc()) {
        float score = scorer.score();
        assertTrue(
            "doc " + doc + " scores " + score + " > getMaxScore(" + upTo + ") = " + maxScore,
            score <= maxScore);
      }
    }
  }
}
