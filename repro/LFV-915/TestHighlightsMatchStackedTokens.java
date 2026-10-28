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
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.FlattenGraphFilter;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.monitor.HighlightsMatch.Hit;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.CharsRefBuilder;

/** Two different hits at the same position (stacked tokens) must both be reported. */
public class TestHighlightsMatchStackedTokens extends LuceneTestCase {

  private static final String FIELD = "f";

  /** "wi fi" gives "wi" [0,2), "wi fi" [0,5) and "fi" [3,5); the first two are at position 0. */
  private static Analyzer shingles() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new WhitespaceTokenizer();
        return new TokenStreamComponents(t, new ShingleFilter(t, 2, 2)); // keeps unigrams
      }
    };
  }

  /** "guinea pig" => "cavy", original kept: "cavy" [0,10) is stacked on "guinea" [0,6). */
  private static Analyzer synonyms() throws IOException {
    SynonymMap.Builder builder = new SynonymMap.Builder(true);
    builder.add(
        SynonymMap.Builder.join(new String[] {"guinea", "pig"}, new CharsRefBuilder()),
        new CharsRef("cavy"),
        true);
    SynonymMap map = builder.build();
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new WhitespaceTokenizer();
        return new TokenStreamComponents(
            t, new FlattenGraphFilter(new SynonymGraphFilter(t, map, true)));
      }
    };
  }

  private static Query either(String term1, String term2) {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term(FIELD, term1)), BooleanClause.Occur.SHOULD)
        .add(new TermQuery(new Term(FIELD, term2)), BooleanClause.Occur.SHOULD)
        .build();
  }

  private static HighlightsMatch match(Analyzer analyzer, String text, Query query)
      throws IOException {
    try (Monitor monitor = new Monitor(analyzer)) {
      monitor.register(new MonitorQuery("q", query));
      Document doc = new Document();
      doc.add(new TextField(FIELD, text, Field.Store.NO));
      HighlightsMatch m = monitor.match(doc, HighlightsMatch.MATCHER).matches("q");
      assertNotNull("the query must match", m);
      return m;
    }
  }

  /** An equals-based copy: contains() of the collection getHits returns uses Hit.compareTo. */
  private static Set<Hit> hits(HighlightsMatch m) {
    return new HashSet<>(m.getHits(FIELD));
  }

  public void testShingleAndUnigramAtSamePosition() throws IOException {
    HighlightsMatch m = match(shingles(), "wi fi", either("wi fi", "wi"));
    // Weight#matches reports both hits: 0(0)->0(2) for "wi" and 0(0)->0(5) for "wi fi"
    assertEquals(Set.of(new Hit(0, 0, 0, 2), new Hit(0, 0, 0, 5)), hits(m));
    assertEquals(2, m.getHitCount());
  }

  public void testMultiWordSynonymAtSamePosition() throws IOException {
    HighlightsMatch m = match(synonyms(), "guinea pig", either("guinea", "cavy"));
    // Weight#matches reports both hits: 0(0)->0(6) for "guinea" and 0(0)->0(10) for "cavy"
    assertEquals(Set.of(new Hit(0, 0, 0, 6), new Hit(0, 0, 0, 10)), hits(m));
    assertEquals(2, m.getHitCount());
  }

  public void testContainsComparesOffsets() throws IOException {
    HighlightsMatch m = match(new WhitespaceAnalyzer(), "wi", new TermQuery(new Term(FIELD, "wi")));
    assertEquals(Set.of(new Hit(0, 0, 0, 2)), hits(m));
    assertFalse(
        "a set holding only 0(0)->0(2) must not contain 0(2)->0(4)",
        m.getHits(FIELD).contains(new Hit(0, 2, 0, 4)));
  }

  public void testMatchesWithDifferentHitsAreNotEqual() throws IOException {
    Query q = new TermQuery(new Term(FIELD, "wi"));
    HighlightsMatch m1 = match(new WhitespaceAnalyzer(), "wi", q); // one hit: 0(0)->0(2)
    HighlightsMatch m2 = match(new WhitespaceAnalyzer(), "  wi", q); // one hit: 0(2)->0(4)
    assertNotEquals(hits(m1), hits(m2));
    assertFalse(
        "equal, although the hits and the hashCodes ("
            + m1.hashCode()
            + ", "
            + m2.hashCode()
            + ") differ",
        m1.equals(m2));
  }
}
