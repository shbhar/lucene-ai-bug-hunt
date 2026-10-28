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
package org.apache.lucene.monitor;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A BooleanClause.Occur.FILTER clause "must appear in matching documents" but "does not
 * participate in scoring" (BooleanClause.Occur javadoc), so a BooleanQuery whose only required
 * clause is a FILTER scores 0. The default QueryDecomposer puts FILTER clauses together with MUST
 * clauses and, when there is exactly one of them and no SHOULD clause, replaces the BooleanQuery
 * by that clause ("decompose the MUST clause instead"). The clause then runs as a scoring query,
 * so ScoringMatch and ExplainingMatch report a positive score for a query that scores 0.
 *
 * <p>Reference: the same query searched on a one-document index of the same document. Public API
 * only.
 */
public class TestMonitorFilterClauseScore extends LuceneTestCase {

  private static final Analyzer ANALYZER = new WhitespaceAnalyzer();
  private static final BM25Similarity SIM = new BM25Similarity();

  private static Document doc() {
    Document doc = new Document();
    doc.add(new TextField("body", "apple banana cherry", Field.Store.NO));
    return doc;
  }

  private static float oneDocScore(Query q) throws IOException {
    IndexSearcher searcher = MemoryIndex.fromDocument(doc(), ANALYZER).createSearcher();
    searcher.setSimilarity(SIM);
    TopDocs td = searcher.search(q, 1);
    assertEquals("query must match the document", 1, td.scoreDocs.length);
    return td.scoreDocs[0].score;
  }

  private static void assertScores(Query q) throws IOException {
    float expected = oneDocScore(q);
    assertEquals("a FILTER-only query scores 0 in a search", 0f, expected, 0f);
    try (Monitor monitor = new Monitor(ANALYZER)) {
      monitor.register(new MonitorQuery("q", q));
      ExplainingMatch em = monitor.match(doc(), ExplainingMatch.MATCHER).matches("q");
      ScoringMatch sm = monitor.match(doc(), ScoringMatch.matchWithSimilarity(SIM)).matches("q");
      assertNotNull("ExplainingMatch must report the matching query " + q, em);
      assertEquals(
          "ExplainingMatch value of " + q, expected, em.getExplanation().getValue().floatValue(), 0f);
      // ScoringMatch does not report matches whose score is 0 (ScoringMatch.doMatch keeps only
      // score > 0); if it reports the query at all, the score must be the query's own score.
      if (sm != null) {
        assertEquals("ScoringMatch score of " + q, expected, sm.getScore(), 0f);
      }
    }
  }

  private static Query apple() {
    return new TermQuery(new Term("body", "apple"));
  }

  /** "#body:apple" */
  public void testFilterOnly() throws IOException {
    assertScores(new BooleanQuery.Builder().add(apple(), BooleanClause.Occur.FILTER).build());
  }

  /** "#body:apple -body:kiwi": the decomposed part becomes "+body:apple -body:kiwi". */
  public void testFilterWithExclusion() throws IOException {
    assertScores(
        new BooleanQuery.Builder()
            .add(apple(), BooleanClause.Occur.FILTER)
            .add(new TermQuery(new Term("body", "kiwi")), BooleanClause.Occur.MUST_NOT)
            .build());
  }

  /** "(#body:apple)^2": the decomposed part becomes "(body:apple)^2.0". */
  public void testBoostedFilterOnly() throws IOException {
    assertScores(
        new BoostQuery(
            new BooleanQuery.Builder().add(apple(), BooleanClause.Occur.FILTER).build(), 2f));
  }

  /** Control: FILTER plus a SHOULD clause is not decomposed (TestQueryDecomposer). Passes. */
  public void testFilterAndShouldControl() throws IOException {
    assertScores(
        new BooleanQuery.Builder()
            .add(apple(), BooleanClause.Occur.FILTER)
            .add(new TermQuery(new Term("body", "kiwi")), BooleanClause.Occur.SHOULD)
            .build());
  }
}
