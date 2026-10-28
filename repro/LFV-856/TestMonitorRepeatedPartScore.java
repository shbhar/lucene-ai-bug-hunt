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
import java.util.Collections;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A registered query in which the same sub-query occurs more than once gets a lower score from
 * ScoringMatch and ExplainingMatch than the query itself gives the document: QueryDecomposer keeps
 * the query's parts in a Set, so the repeated part is kept, run and scored once, while BooleanQuery
 * scores it at every occurrence.
 *
 * <p>Document: body = "red green apple". With BM25 on a one-document index every one of the three
 * terms scores the same, s = 0.13076457. Reference: IndexSearcher over a one-document index of the
 * same document, BM25 on both sides. Queries come from the classic QueryParser.
 */
public class TestMonitorRepeatedPartScore extends LuceneTestCase {

  private static final String FIELD = "body";
  private static final Analyzer ANALYZER = new StandardAnalyzer();
  private static final BM25Similarity SIM = new BM25Similarity();

  /** A decomposer that never splits: the workaround available today. */
  private static final QueryDecomposer KEEP_WHOLE =
      new QueryDecomposer() {
        @Override
        public Set<Query> decompose(Query q) {
          return Collections.singleton(q);
        }
      };

  private static Document doc() {
    Document doc = new Document();
    doc.add(new TextField(FIELD, "red green apple", Field.Store.NO));
    return doc;
  }

  private static Query parse(String query) throws Exception {
    return new QueryParser(FIELD, ANALYZER).parse(query);
  }

  /** The score the query itself gives the document. */
  private static float searcherScore(Query q) throws IOException {
    IndexSearcher searcher = MemoryIndex.fromDocument(doc(), ANALYZER).createSearcher();
    searcher.setSimilarity(SIM);
    TopDocs td = searcher.search(q, 1);
    assertEquals("the query must match the document: " + q, 1, td.scoreDocs.length);
    return td.scoreDocs[0].score;
  }

  private static <T extends QueryMatch> T monitorMatch(
      Query q, MatcherFactory<T> matcher, QueryDecomposer decomposer) throws IOException {
    MonitorConfiguration config = new MonitorConfiguration().setQueryDecomposer(decomposer);
    try (Monitor monitor = new Monitor(ANALYZER, config)) {
      monitor.register(new MonitorQuery("q", q));
      T match = monitor.match(doc(), matcher).matches("q");
      assertNotNull("the Monitor must report a match for " + q, match);
      return match;
    }
  }

  private static float scoringMatch(Query q, QueryDecomposer decomposer) throws IOException {
    return monitorMatch(q, ScoringMatch.matchWithSimilarity(SIM), decomposer).getScore();
  }

  private static void assertMonitorScoreIsQueryScore(String query) throws Exception {
    Query q = parse(query);
    float s = searcherScore(parse("apple"));
    assertEquals(
        "ScoringMatch score of " + q + " (each term alone scores " + s + ")",
        searcherScore(q),
        scoringMatch(q, new QueryDecomposer()),
        1e-6f);
  }

  /** "(red apple) (green apple)": IndexSearcher 4 x s = 0.5230583, Monitor 3 x s = 0.39229372. */
  public void testTermSharedByTwoDisjunctions() throws Exception {
    assertMonitorScoreIsQueryScore("(red apple) (green apple)");
  }

  /** "apple apple": IndexSearcher 2 x s = 0.26152915, Monitor s = 0.13076457. */
  public void testRepeatedShouldClause() throws Exception {
    assertMonitorScoreIsQueryScore("apple apple");
  }

  /** "+apple +apple": IndexSearcher 2 x s = 0.26152915, Monitor s = 0.13076457. */
  public void testRepeatedMustClause() throws Exception {
    assertMonitorScoreIsQueryScore("+apple +apple");
  }

  /** ExplainingMatch reports the same lower value, as a "sum of:" the distinct parts. */
  public void testExplainingMatch() throws Exception {
    Query q = parse("(red apple) (green apple)");
    ExplainingMatch match = monitorMatch(q, ExplainingMatch.MATCHER, new QueryDecomposer());
    assertEquals(
        "ExplainingMatch value of " + q + ": " + match.getExplanation(),
        searcherScore(q),
        match.getExplanation().getValue().floatValue(),
        1e-6f);
  }

  /**
   * Controls, all pass today: with no repeated part the Monitor's score is the query's score; a
   * decomposer that keeps the query whole gives the query's score; and matching is not affected.
   */
  public void testControls() throws Exception {
    Query distinct = parse("(red apple) (green pie)");
    assertEquals(searcherScore(distinct), scoringMatch(distinct, new QueryDecomposer()), 1e-6f);
    for (String query :
        new String[] {"(red apple) (green apple)", "apple apple", "+apple +apple"}) {
      Query q = parse(query);
      assertEquals(query, searcherScore(q), scoringMatch(q, KEEP_WHOLE), 1e-6f);
      assertNotNull(query, monitorMatch(q, QueryMatch.SIMPLE_MATCHER, new QueryDecomposer()));
    }
  }
}
