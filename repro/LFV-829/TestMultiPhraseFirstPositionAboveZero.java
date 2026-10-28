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
package org.apache.lucene.search.highlight;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.spans.SpanNearQuery;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockSynonymFilter;
import org.apache.lucene.tests.analysis.MockTokenFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * Classic Highlighter + QueryScorer on a MultiPhraseQuery whose first position is above 0 (what
 * QueryBuilder produces for a quoted query with a leading stop word and a synonym). The oracle is
 * the set of positions reported by Weight#matches on an index of the same text; the controls are
 * the same query at positions starting from 0 and the PhraseQuery with the same positions.
 */
public class TestMultiPhraseFirstPositionAboveZero extends LuceneTestCase {

  private static final String F = "body";

  /** whitespace + lowercase, English stop words removed (holes kept), "dogs" gets synonym "dog" */
  private static Analyzer analyzer() {
    return analyzer(true);
  }

  private static Analyzer analyzer(boolean checks) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        MockTokenizer tok = new MockTokenizer(MockTokenizer.WHITESPACE, true);
        tok.setEnableChecks(checks);
        TokenStream ts = new MockTokenFilter(tok, MockTokenFilter.ENGLISH_STOPSET);
        ts = new MockSynonymFilter(ts);
        return new TokenStreamComponents(tok, ts);
      }
    };
  }

  private static Term t(String s) {
    return new Term(F, s);
  }

  private static MultiPhraseQuery mpq(int firstPos, int slop) {
    MultiPhraseQuery.Builder b = new MultiPhraseQuery.Builder();
    b.add(new Term[] {t("dogs"), t("dog")}, firstPos);
    b.add(new Term[] {t("bark")}, firstPos + 1);
    b.setSlop(slop);
    return b.build();
  }

  private static String highlight(Analyzer a, Query q, String text) throws Exception {
    Highlighter h = new Highlighter(new SimpleHTMLFormatter(), new QueryScorer(q, F));
    h.setTextFragmenter(new NullFragmenter());
    String s = h.getBestFragment(a, F, text);
    return s == null ? text : s;
  }

  /** Positions covered by the matches Weight#matches reports for the single document. */
  private static TreeSet<Integer> matchedPositions(Analyzer a, Query q, String text)
      throws IOException {
    TreeSet<Integer> out = new TreeSet<>();
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document d = new Document();
        d.add(new TextField(F, text, Field.Store.NO));
        w.addDocument(d);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        IndexSearcher s = new IndexSearcher(r);
        Weight wt = s.createWeight(s.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1f);
        Matches m = wt.matches(r.leaves().get(0), 0);
        if (m != null) {
          MatchesIterator it = m.getMatches(F);
          while (it != null && it.next()) {
            for (int p = it.startPosition(); p <= it.endPosition(); p++) {
              out.add(p);
            }
          }
        }
      }
    }
    return out;
  }

  /** Text with exactly the words at the matched positions wrapped (text has no stop words). */
  private static String render(String text, TreeSet<Integer> positions) {
    String[] words = text.split(" ");
    List<String> out = new ArrayList<>();
    for (int i = 0; i < words.length; i++) {
      out.add(positions.contains(i) ? "<B>" + words[i] + "</B>" : words[i]);
    }
    return String.join(" ", out);
  }

  private static void check(Analyzer a, Query q, String text, String label) throws Exception {
    TreeSet<Integer> pos = matchedPositions(a, q, text);
    assertFalse(label + ": the query must match the document", pos.isEmpty());
    String expected = render(text, pos);
    String actual = highlight(a, q, text);
    System.out.println(
        "[" + label + "] query=" + q + " matchPositions=" + pos + "\n   expected: " + expected
            + "\n   actual:   " + actual);
    assertEquals(label + " (query " + q + ")", expected, actual);
  }

  // text: dogs(0) rarely(1) bark(2) when(3) old(4) dogs(5) bark(6); only 5-6 is the phrase
  private static final String TEXT = "dogs rarely bark when old dogs bark";

  // text for slop 1: dogs(0) never(1) ever(2) bark(3) when(4) old(5) dogs(6) bark(7)
  private static final String TEXT_SLOP = "dogs never ever bark when old dogs bark";

  /** control 1: positions 0,1 */
  public void testControlMultiPhraseFromZero() throws Exception {
    Analyzer a = analyzer();
    check(a, mpq(0, 0), TEXT, "mpq@0 slop0");
    a.close();
  }

  /** control 2: PhraseQuery at positions 1,2 */
  public void testControlPhraseQueryFromOne() throws Exception {
    Analyzer a = analyzer();
    PhraseQuery pq = new PhraseQuery.Builder().add(t("dogs"), 1).add(t("bark"), 2).build();
    check(a, pq, TEXT, "pq@1 slop0");
    a.close();
  }

  /** the claim, direct construction: positions 1,2, slop 0 */
  public void testMultiPhraseFromOne() throws Exception {
    Analyzer a = analyzer();
    // the matcher itself only looks at relative positions: same matches as from 0
    assertEquals(
        matchedPositions(a, mpq(0, 0), TEXT), matchedPositions(a, mpq(1, 0), TEXT));
    check(a, mpq(1, 0), TEXT, "mpq@1 slop0");
    a.close();
  }

  /** the claim, through QueryBuilder (as used by the query parsers): "the dogs bark" */
  public void testQueryBuilderLeadingStopWord() throws Exception {
    Analyzer a = analyzer();
    Query q = new QueryBuilder(a).createPhraseQuery(F, "the dogs bark");
    assertTrue("expected MultiPhraseQuery, got " + q, q instanceof MultiPhraseQuery);
    assertEquals("[1, 2]", Arrays.toString(((MultiPhraseQuery) q).getPositions()));
    check(a, q, TEXT, "QueryBuilder 'the dogs bark'");
    a.close();
  }

  /** two leading stop words: "the a dogs bark" -> positions 2,3 */
  public void testQueryBuilderTwoLeadingStopWords() throws Exception {
    Analyzer a = analyzer();
    Query q = new QueryBuilder(a).createPhraseQuery(F, "the a dogs bark");
    assertTrue("expected MultiPhraseQuery, got " + q, q instanceof MultiPhraseQuery);
    assertEquals("[2, 3]", Arrays.toString(((MultiPhraseQuery) q).getPositions()));
    check(a, q, TEXT_SLOP, "QueryBuilder 'the a dogs bark'");
    a.close();
  }

  /** control for slop 1: positions 0,1 */
  public void testControlSlopOneFromZero() throws Exception {
    Analyzer a = analyzer();
    check(a, mpq(0, 1), TEXT_SLOP, "mpq@0 slop1");
    a.close();
  }

  /** slop 1 at positions 1,2: claim says one extra slop */
  public void testSlopOneFromOne() throws Exception {
    Analyzer a = analyzer();
    assertEquals(
        matchedPositions(a, mpq(0, 1), TEXT_SLOP), matchedPositions(a, mpq(1, 1), TEXT_SLOP));
    check(a, mpq(1, 1), TEXT_SLOP, "mpq@1 slop1");
    a.close();
  }

  private static String spans(WeightedSpanTerm w) {
    StringBuilder sb = new StringBuilder();
    for (PositionSpan ps : w.getPositionSpans()) {
      sb.append('[').append(ps.start).append(',').append(ps.end).append(']');
    }
    return sb.toString();
  }

  /**
   * Mechanism (diagnostic, uses the protected extension point): record the SpanQuery that the
   * extractor builds for the same MultiPhraseQuery at positions starting from 0 and from 1.
   */
  public void testMechanismSpanNearSlop() throws Exception {
    Map<String, String> seen = new HashMap<>();
    for (int first : new int[] {0, 1, 2}) {
      final int f = first;
      WeightedSpanTermExtractor ex =
          new WeightedSpanTermExtractor(F) {
            @Override
            protected void extractWeightedSpanTerms(
                Map<String, WeightedSpanTerm> terms, SpanQuery spanQuery, float boost)
                throws IOException {
              String desc = spanQuery.toString();
              if (spanQuery instanceof SpanNearQuery) {
                SpanNearQuery snq = (SpanNearQuery) spanQuery;
                desc += " slop=" + snq.getSlop() + " inOrder=" + snq.isInOrder();
              }
              seen.put("first=" + f, desc);
              super.extractWeightedSpanTerms(terms, spanQuery, boost);
            }
          };
      ex.setMaxDocCharsToAnalyze(Integer.MAX_VALUE); // as QueryScorer does (default is 0)
      // direct extractor use (outside the Highlighter workflow): no MockTokenizer workflow checks
      Analyzer a = analyzer(false);
      // the extractor consumes and closes the stream (MemoryIndex)
      TokenStream ts = a.tokenStream(F, TEXT);
      Map<String, WeightedSpanTerm> m = ex.getWeightedSpanTerms(mpq(first, 0), 1f, ts, F);
      WeightedSpanTerm bark = m.get("bark");
      System.out.println(
          "[mechanism] first="
              + first
              + " span="
              + seen.get("first=" + first)
              + " bark@2 in span="
              + (bark != null && bark.checkPosition(2))
              + " bark@6 in span="
              + (bark != null && bark.checkPosition(6))
              + " terms="
              + m.keySet()
              + " barkSpans="
              + (bark == null ? "null" : spans(bark)));
      a.close();
    }
    assertEquals(
        "the SpanNearQuery for positions 1,2 should equal the one for 0,1",
        seen.get("first=0"),
        seen.get("first=1"));
  }
}
