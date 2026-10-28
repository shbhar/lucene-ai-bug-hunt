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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.FunctionScoreQuery;
import org.apache.lucene.queries.spans.SpanNearQuery;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndexSearcher#explain must not throw for a document of a legal query. BooleanWeight#explain
 * computes its value with scorer(context).score() even when the BooleanWeight was created with
 * ScoreMode.COMPLETE_NO_SCORES (a FILTER or MUST_NOT clause, the inner query of a
 * FunctionScoreQuery whose source does not need scores), and a span query's scorer cannot score
 * without scores.
 */
public class TestExplainNonScoringBooleanClause extends LuceneTestCase {

  private Directory dir;
  private IndexReader reader;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      for (String text : new String[] {"quick brown fox", "quick red fox", "lazy dog"}) {
        Document doc = new Document();
        doc.add(new TextField("body", text, Field.Store.NO));
        doc.add(new FloatDocValuesField("popularity", 2.5f));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  /** A searcher with the production default of IndexSearcher on main: no query cache. */
  private IndexSearcher searcherWithoutCache() {
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setQueryCache(null);
    return searcher;
  }

  private static Query brownFox() {
    return new SpanNearQuery(
        new SpanQuery[] {
          new SpanTermQuery(new Term("body", "brown")), new SpanTermQuery(new Term("body", "fox"))
        },
        0,
        true);
  }

  /** brown-fox span OR dog */
  private static Query spanOrDog() {
    return new BooleanQuery.Builder()
        .add(brownFox(), BooleanClause.Occur.SHOULD)
        .add(new TermQuery(new Term("body", "dog")), BooleanClause.Occur.SHOULD)
        .build();
  }

  /** Asserts that explain agrees with the hits for every document, and does not throw. */
  private static void assertExplainAgreesWithHits(IndexSearcher searcher, Query query)
      throws IOException {
    TopDocs hits = searcher.search(query, 10);
    int maxDoc = searcher.getIndexReader().maxDoc();
    for (int doc = 0; doc < maxDoc; doc++) {
      float score = Float.NaN;
      for (ScoreDoc sd : hits.scoreDocs) {
        if (sd.doc == doc) score = sd.score;
      }
      Explanation explanation = searcher.explain(query, doc);
      assertEquals("isMatch of doc " + doc, Float.isNaN(score) == false, explanation.isMatch());
      if (explanation.isMatch()) {
        assertEquals("explained value of doc " + doc, score, explanation.getValue().floatValue(), 0f);
      }
    }
  }

  /** +quick #(brown-fox-span OR dog): the span query sits in a FILTER clause. */
  public void testSpanQueryInsideFilterClause() throws IOException {
    Query query =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("body", "quick")), BooleanClause.Occur.MUST)
            .add(spanOrDog(), BooleanClause.Occur.FILTER)
            .build();
    IndexSearcher searcher = searcherWithoutCache();
    assertEquals(1, searcher.count(query));
    assertExplainAgreesWithHits(searcher, query);
  }

  /** +quick -(brown-fox-span OR dog): explaining the excluded doc 0 must say "no match". */
  public void testSpanQueryInsideMustNotClause() throws IOException {
    Query query =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("body", "quick")), BooleanClause.Occur.MUST)
            .add(spanOrDog(), BooleanClause.Occur.MUST_NOT)
            .build();
    IndexSearcher searcher = searcherWithoutCache();
    assertEquals(1, searcher.count(query));
    assertExplainAgreesWithHits(searcher, query);
  }

  /**
   * FunctionScoreQuery over a BooleanQuery with a span clause, scored by a doc-values field: the
   * wrapped BooleanQuery is weighted without scores. Fails with and without a query cache.
   */
  public void testFunctionScoreQueryOverBooleanQueryWithSpanClause() throws IOException {
    Query query =
        new FunctionScoreQuery(spanOrDog(), DoubleValuesSource.fromFloatField("popularity"));
    IndexSearcher noCache = searcherWithoutCache();
    assertEquals(2, noCache.count(query));
    assertExplainAgreesWithHits(noCache, query);
  }

  /** Same query with the query cache that IndexSearcher used by default up to 10.x. */
  public void testFunctionScoreQueryWithTenXDefaultQueryCache() throws IOException {
    Query query =
        new FunctionScoreQuery(spanOrDog(), DoubleValuesSource.fromFloatField("popularity"));
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setQueryCache(new LRUQueryCache(1000, 32L * 1024 * 1024));
    searcher.setQueryCachingPolicy(new UsageTrackingQueryCachingPolicy());
    assertExplainAgreesWithHits(searcher, query);
  }
}
