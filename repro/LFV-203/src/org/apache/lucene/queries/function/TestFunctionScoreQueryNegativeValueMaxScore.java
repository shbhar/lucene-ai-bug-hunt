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
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * FunctionScoreQuery scores a document whose value is negative as 0, but its block-max bound
 * (#16431) passes the negative block maximum through, so getMaxScore returns a negative number for
 * a block whose documents all score 0. A disjunction then subtracts that negative bound from the
 * bounds of its other clauses and skips competitive documents.
 */
public class TestFunctionScoreQueryNegativeValueMaxScore extends LuceneTestCase {

  // more than one 4096-doc skip-index interval and scoring window
  private static final int NUM_DOCS = 5_000;

  private static Directory indexDocs() throws Exception {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < NUM_DOCS; i++) {
        Document doc = new Document();
        long delta;
        if (i == 0) {
          delta = 1; // scores 1 through the function, does not match "b"
        } else {
          delta = -1000; // negative: the function scores 0 for these documents
        }
        // every document has the text field, the last one is the only match of "b" (the best hit)
        doc.add(new StringField("body", i == NUM_DOCS - 1 ? "b" : "x", Field.Store.NO));
        doc.add(NumericDocValuesField.indexedField("delta", delta));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  private static Query functionScore() {
    return new FunctionScoreQuery(
        MatchAllDocsQuery.INSTANCE, DoubleValuesSource.fromLongField("delta"));
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
      // the last doc (matches "b", function score 0) is the best hit
      assertEquals(NUM_DOCS - 1, complete.scoreDocs[0].doc);
      assertTrue(complete.scoreDocs[0].score > 1f);
      assertEquals(
          "TOP_SCORES top hit (score " + topScores.scoreDocs[0].score + ") differs from COMPLETE",
          complete.scoreDocs[0].doc,
          topScores.scoreDocs[0].doc);
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
      DocIdSetIterator it = scorer.iterator();
      int upTo = -1;
      float maxScore = Float.NaN;
      for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
        if (doc > upTo) {
          upTo = scorer.advanceShallow(doc);
          maxScore = scorer.getMaxScore(upTo);
        }
        float score = scorer.score();
        assertTrue(
            "doc " + doc + " scores " + score + " > getMaxScore(" + upTo + ") = " + maxScore,
            score <= maxScore);
      }
    }
  }
}
