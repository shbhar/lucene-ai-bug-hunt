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

import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Three query constructors validate a float/double parameter with a range check of the form {@code
 * x < lo || x > hi} (or {@code x <= 0}); NaN fails every comparison, so it passes the check that
 * is meant to reject it.
 */
public class TestQueryRangeChecksAdmitNaN extends LuceneTestCase {

  private static List<Query> disjuncts() {
    return List.of(new TermQuery(new Term("f", "a")), new TermQuery(new Term("f", "b")));
  }

  /** DisjunctionMaxQuery: "tieBreakerMultiplier must be in [0, 1]". */
  public void testDisjunctionMaxTieBreakerNaN() {
    expectThrows(IllegalArgumentException.class, () -> new DisjunctionMaxQuery(disjuncts(), Float.NaN));
  }

  /** LatLonPoint.newDistanceFeatureQuery: "pivotDistance must be > 0". */
  public void testDistanceFeaturePivotNaN() {
    expectThrows(
        IllegalArgumentException.class,
        () -> LatLonPoint.newDistanceFeatureQuery("loc", 1f, 0, 0, Double.NaN));
  }

  /** BayesianScoreQuery: "baseRate must be in [0, 1)". */
  public void testBayesianBaseRateNaN() {
    expectThrows(
        IllegalArgumentException.class,
        () -> new BayesianScoreQuery(new TermQuery(new Term("f", "a")), 1f, 0f, Float.NaN));
  }

  /** Consequence 1 of the accepted NaN tie-breaker: the query is not equal to itself. */
  public void testDisjunctionMaxNaNNotReflexive() {
    Query q;
    try {
      q = new DisjunctionMaxQuery(disjuncts(), Float.NaN);
    } catch (IllegalArgumentException expected) {
      return; // fixed: rejected up front
    }
    assertTrue("Query#equals must be reflexive", q.equals(q));
  }

  /** Consequence 2 of the accepted NaN tie-breaker: every hit scores NaN. */
  public void testDisjunctionMaxNaNScores() throws Exception {
    Query q;
    try {
      q = new DisjunctionMaxQuery(disjuncts(), Float.NaN);
    } catch (IllegalArgumentException expected) {
      return; // fixed: rejected up front
    }
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        for (String s : new String[] {"a b", "a", "b"}) {
          Document d = new Document();
          d.add(new TextField("f", s, Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        for (ScoreDoc sd : searcher.search(q, 10).scoreDocs) {
          assertFalse("doc " + sd.doc + " scored NaN", Float.isNaN(sd.score));
        }
      }
    }
  }
}
