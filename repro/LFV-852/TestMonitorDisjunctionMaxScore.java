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
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A DisjunctionMaxQuery registered in a Monitor should get the score that IndexSearcher gives it on
 * the same documents: the best disjunct plus tieBreakerMultiplier times the others, "not the sum of
 * the field scores (as BooleanQuery would give)" (DisjunctionMaxQuery javadoc). Public API only;
 * BM25 on both sides.
 */
public class TestMonitorDisjunctionMaxScore extends LuceneTestCase {

  private static final float EPS = 1e-6f;

  private static Document doc(String title, String body) {
    Document d = new Document();
    d.add(new TextField("title", title, Field.Store.NO));
    d.add(new TextField("body", body, Field.Store.NO));
    return d;
  }

  private static Query term(String field, String text) {
    return new TermQuery(new Term(field, text));
  }

  private static Query dismax(float tie, String text) {
    return new DisjunctionMaxQuery(List.of(term("title", text), term("body", text)), tie);
  }

  /** The score IndexSearcher gives q on each document of a plain index of docs. */
  private static float[] searcherScores(Query q, Document... docs) throws IOException {
    try (Analyzer analyzer = new StandardAnalyzer();
        Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        for (Document d : docs) {
          w.addDocument(d);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        float[] scores = new float[docs.length];
        for (int i = 0; i < docs.length; i++) {
          scores[i] = searcher.explain(q, i).getValue().floatValue();
        }
        return scores;
      }
    }
  }

  /** The score ScoringMatch reports for q, registered alone, on each document of the batch. */
  private static float[] monitorScores(Query q, Document... docs) throws IOException {
    try (Analyzer analyzer = new StandardAnalyzer();
        Monitor monitor = new Monitor(analyzer)) {
      monitor.register(new MonitorQuery("q", q));
      MultiMatchingQueries<ScoringMatch> matches =
          monitor.match(docs, ScoringMatch.DEFAULT_MATCHER);
      float[] scores = new float[docs.length];
      for (int i = 0; i < docs.length; i++) {
        scores[i] = matches.matches("q", i).getScore();
      }
      return scores;
    }
  }

  // The two disjuncts score differently on this document, so the sum cannot pass for 2 x max.
  private static final Document APPLE = doc("apple apple", "apple banana cherry date");

  public void testDisMaxTieZero() throws IOException {
    float title = searcherScores(term("title", "apple"), APPLE)[0];
    float body = searcherScores(term("body", "apple"), APPLE)[0];
    Query q = dismax(0f, "apple");
    float expected = searcherScores(q, APPLE)[0];
    assertEquals("IndexSearcher follows the javadoc: max", Math.max(title, body), expected, EPS);
    assertEquals(
        "ScoringMatch score of " + q + " (disjuncts: title " + title + ", body " + body + ")",
        expected,
        monitorScores(q, APPLE)[0],
        EPS);
  }

  public void testDisMaxTieOneTenth() throws IOException {
    Query q = dismax(0.1f, "apple");
    assertEquals(
        "ScoringMatch score of " + q, searcherScores(q, APPLE)[0], monitorScores(q, APPLE)[0], EPS);
  }

  public void testExplainingMatchDisMaxTieZero() throws IOException {
    Query q = dismax(0f, "apple");
    float actual;
    try (Analyzer analyzer = new StandardAnalyzer();
        Monitor monitor = new Monitor(analyzer)) {
      monitor.register(new MonitorQuery("q", q));
      ExplainingMatch m = monitor.match(APPLE, ExplainingMatch.MATCHER).matches("q");
      actual = m.getExplanation().getValue().floatValue();
    }
    assertEquals("ExplainingMatch value of " + q, searcherScores(q, APPLE)[0], actual, EPS);
  }

  /** The javadoc's own example: albino + elephant must outrank albino in both fields. */
  public void testAlbinoElephantRanking() throws IOException {
    Document a = doc("albino", "albino");
    Document b = doc("albino", "elephant");
    Query q =
        new BooleanQuery.Builder()
            .add(dismax(0f, "albino"), Occur.SHOULD)
            .add(dismax(0f, "elephant"), Occur.SHOULD)
            .build();
    float[] ref = searcherScores(q, a, b);
    float[] mon = monitorScores(q, a, b);
    assertTrue("IndexSearcher ranks B above A", ref[1] > ref[0]);
    assertTrue(
        "Monitor: B (albino + elephant) = "
            + mon[1]
            + " must outscore A (albino twice) = "
            + mon[0]
            + "; IndexSearcher: A = "
            + ref[0]
            + ", B = "
            + ref[1],
        mon[1] > mon[0]);
  }

  /** Controls (pass today): tie 1 and a BooleanQuery disjunction really are sums. */
  public void testSumControls() throws IOException {
    Query tieOne = dismax(1f, "apple");
    Query bool =
        new BooleanQuery.Builder()
            .add(term("title", "apple"), Occur.SHOULD)
            .add(term("body", "apple"), Occur.SHOULD)
            .build();
    for (Query q : List.of(tieOne, bool)) {
      assertEquals("score of " + q, searcherScores(q, APPLE)[0], monitorScores(q, APPLE)[0], EPS);
    }
  }
}
