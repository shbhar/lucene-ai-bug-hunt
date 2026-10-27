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
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.DoubleValues;
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
 * DoubleValuesSource's skip-index bound (#16431) narrows the decoded block maximum to the nearest
 * float, which can be below the maximum value. FunctionScoreQuery then multiplies that bound by the
 * boost while it computes the score as (float) (value * boost) from the exact value, so the bound
 * can be one float ulp below the score. Here value = 2^24 + 1 (not a float) and boost = 1.5: the
 * value bound is 2^24, the score is 25165826, the score bound is 25165824.
 */
public class TestFunctionScoreQueryMaxScoreRounding extends LuceneTestCase {

  // more than one 4096-doc skip-index interval and scoring window
  private static final int NUM_DOCS = 5_000;
  private static final float BOOST = 1.5f;

  private static Directory indexDocs() throws Exception {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < NUM_DOCS; i++) {
        Document doc = new Document();
        long count = 0;
        if (i == 0) {
          count = 1L << 24; // scores 1.5 * 2^24 = 25165824
        } else if (i == NUM_DOCS - 1) {
          count = (1L << 24) + 1; // scores (float) (1.5 * (2^24 + 1)) = 25165826, the best hit
        }
        doc.add(new StringField("body", i == 1 ? "b" : "x", Field.Store.NO));
        doc.add(NumericDocValuesField.indexedField("count", count));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  private static Query functionScore() {
    return new BoostQuery(
        new FunctionScoreQuery(MatchAllDocsQuery.INSTANCE, DoubleValuesSource.fromLongField("count")),
        BOOST);
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
      assertEquals(NUM_DOCS - 1, complete.scoreDocs[0].doc);
      assertEquals(25165826f, complete.scoreDocs[0].score, 0f);
      assertEquals(
          "TOP_SCORES top hit (score " + topScores.scoreDocs[0].score + ") differs from COMPLETE",
          complete.scoreDocs[0].doc,
          topScores.scoreDocs[0].doc);
    }
  }

  /** DoubleValues#getMaxScore must bound the values of the block (as TestDoubleValuesSource checks). */
  public void testValueBound() throws Exception {
    try (Directory dir = indexDocs();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      LeafReaderContext leaf = reader.leaves().get(0);
      DoubleValues values = DoubleValuesSource.fromLongField("count").getValues(leaf, null);
      int doc = NUM_DOCS - 1;
      int upTo = values.advanceShallow(doc);
      assertTrue(values.advanceExact(doc));
      double value = values.doubleValue();
      assertEquals((1L << 24) + 1, value, 0d);
      assertTrue(
          "value " + value + " > getMaxScore(" + upTo + ") = " + values.getMaxScore(upTo),
          value <= values.getMaxScore(upTo));
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
