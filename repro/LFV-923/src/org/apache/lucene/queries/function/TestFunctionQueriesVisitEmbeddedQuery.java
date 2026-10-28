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
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.valuesource.QueryValueSource;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Function queries execute a query that sits inside their values source (the boost query of
 * FunctionScoreQuery.boostByQuery, DoubleValuesSource.fromQuery, QueryValueSource), but their
 * visit() never reaches it, so IndexSearcher's cumulative clause limit does not count its clauses.
 * Each test compares with the BooleanQuery that holds the same clauses; maxClauseCount is the
 * default 1024 and every query below holds 1200 term clauses in total.
 */
public class TestFunctionQueriesVisitEmbeddedQuery extends LuceneTestCase {

  /** A disjunction of n distinct term clauses on field f. */
  private static Query disjunction(String prefix, int n) {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (int i = 0; i < n; i++) {
      b.add(new TermQuery(new Term("f", prefix + i)), Occur.SHOULD);
    }
    return b.build();
  }

  private static void assertRejected(IndexSearcher searcher, Query query) {
    expectThrows(IndexSearcher.TooManyNestedClauses.class, () -> searcher.search(query, 10));
  }

  private interface Body {
    void run(IndexSearcher searcher) throws Exception;
  }

  private void withSearcher(Body body) throws Exception {
    assertEquals(1024, IndexSearcher.getMaxClauseCount());
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 20; i++) {
        Document doc = new Document();
        doc.add(new StringField("f", "a" + i, Field.Store.NO));
        doc.add(new StringField("f", "b" + i, Field.Store.NO));
        w.addDocument(doc);
      }
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        body.run(searcher);
      }
    }
  }

  /** FunctionScoreQuery.boostByQuery(in, boostMatch, boost) runs boostMatch to score each hit. */
  public void testBoostByQueryClausesCountTowardsClauseLimit() throws Exception {
    withSearcher(
        searcher -> {
          Query in = disjunction("a", 600);
          Query boostMatch = disjunction("b", 600);
          // control: the same 1200 clauses as a BooleanQuery are rejected
          assertRejected(
              searcher,
              new BooleanQuery.Builder().add(in, Occur.MUST).add(boostMatch, Occur.SHOULD).build());
          // the boost query runs as well (it changes the scores), so the total is still 1200
          assertRejected(searcher, FunctionScoreQuery.boostByQuery(in, boostMatch, 2f));
        });
  }

  /**
   * Each embedded query is checked only on its own, with a fresh budget, so the total has no bound:
   * 100 boostByQuery clauses with a 1000-clause boost query each run 100,100 term clauses.
   */
  public void testRepeatedBoostByQueryHasNoCumulativeBound() throws Exception {
    withSearcher(
        searcher -> {
          BooleanQuery.Builder b = new BooleanQuery.Builder();
          for (int i = 0; i < 100; i++) {
            b.add(
                FunctionScoreQuery.boostByQuery(
                    new TermQuery(new Term("f", "a" + (i % 20))), disjunction("b", 1000), 2f),
                Occur.SHOULD);
          }
          assertRejected(searcher, b.build());
        });
  }

  /** The same through FunctionScoreQuery(in, DoubleValuesSource.fromQuery(q)). */
  public void testFromQuerySourceClausesCountTowardsClauseLimit() throws Exception {
    withSearcher(
        searcher ->
            assertRejected(
                searcher,
                new FunctionScoreQuery(
                    disjunction("a", 600),
                    DoubleValuesSource.fromQuery(disjunction("b", 600)))));
  }

  /** FunctionMatchQuery decides matching from the score of its embedded query. */
  public void testFunctionMatchQueryClausesCountTowardsClauseLimit() throws Exception {
    withSearcher(
        searcher ->
            assertRejected(
                searcher,
                new BooleanQuery.Builder()
                    .add(disjunction("a", 600), Occur.MUST)
                    .add(
                        new FunctionMatchQuery(
                            DoubleValuesSource.fromQuery(disjunction("b", 600)), v -> v > 0),
                        Occur.FILTER)
                    .build()));
  }

  /** Same omission in the ValueSource API: FunctionRangeQuery over a QueryValueSource. */
  public void testFunctionRangeQueryOverQueryValueSourceClausesCountTowardsClauseLimit()
      throws Exception {
    withSearcher(
        searcher ->
            assertRejected(
                searcher,
                new BooleanQuery.Builder()
                    .add(disjunction("a", 600), Occur.MUST)
                    .add(
                        new FunctionRangeQuery(
                            new QueryValueSource(disjunction("b", 600), 0f),
                            0.001f,
                            1e9f,
                            true,
                            true),
                        Occur.FILTER)
                    .build()));
  }
}
