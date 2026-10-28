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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.FlattenGraphFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.CharsRefBuilder;
import org.junit.Test;

/**
 * Version-portable check (8.2.0 .. main, public API only, plain JUnit 4 so the same source compiles
 * against every release): HighlightsMatch must keep every distinct hit that Weight#matches reports,
 * including two hits that share start and end positions but have different offsets (stacked
 * tokens). HighlightsMatch.Hit.compareTo orders by positions only and the hits live in TreeSets, so
 * one of the two is dropped.
 *
 * <p>The document field indexes offsets, so the hits carry real offsets on every version (before
 * 8.6.0, Monitor.match(Document) indexed the document with IndexWriter, which only records offsets
 * when the field type asks for them). Reference: Weight#matches on a plain IndexWriter index of the
 * same document.
 */
public class TestHitCompareToHistory {

  static final String F = "f";

  static FieldType offsetsType() {
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    ft.freeze();
    return ft;
  }

  /** Whitespace tokens plus 2-word shingles with unigrams: "wi" [0,2) and "wi fi" [0,5) at position 0. */
  static Analyzer shingleAnalyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new WhitespaceTokenizer();
        return new TokenStreamComponents(t, new ShingleFilter(t, 2, 2));
      }
    };
  }

  /** Multi-word synonym "guinea pig" => "cavy" (original kept), flattened for indexing. */
  static Analyzer synonymGraphAnalyzer() throws IOException {
    SynonymMap.Builder b = new SynonymMap.Builder(true);
    CharsRef in = SynonymMap.Builder.join(new String[] {"guinea", "pig"}, new CharsRefBuilder());
    b.add(in, new CharsRef("cavy"), true);
    final SynonymMap map = b.build();
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new WhitespaceTokenizer();
        return new TokenStreamComponents(
            t, new FlattenGraphFilter(new SynonymGraphFilter(t, map, true)));
      }
    };
  }

  static Document doc(String text) {
    Document d = new Document();
    d.add(new Field(F, text, offsetsType()));
    return d;
  }

  /** Every distinct (startPos, startOff, endPos, endOff) that Weight#matches reports for doc 0. */
  static Set<HighlightsMatch.Hit> reference(Analyzer analyzer, String text, Query q)
      throws IOException {
    Set<HighlightsMatch.Hit> out = new HashSet<>();
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      w.addDocument(doc(text));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        Weight wt = s.createWeight(s.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1);
        for (LeafReaderContext ctx : r.leaves()) {
          for (int d = 0; d < ctx.reader().maxDoc(); d++) {
            Matches m = wt.matches(ctx, d);
            if (m == null) continue;
            MatchesIterator mi = m.getMatches(F);
            if (mi == null) continue;
            while (mi.next()) {
              out.add(
                  new HighlightsMatch.Hit(
                      mi.startPosition(), mi.startOffset(), mi.endPosition(), mi.endOffset()));
            }
          }
        }
      }
    }
    return out;
  }

  static HighlightsMatch monitorMatch(Analyzer analyzer, String text, Query q, boolean decompose)
      throws IOException {
    MonitorConfiguration cfg = new MonitorConfiguration();
    if (decompose == false) {
      cfg.setQueryDecomposer(
          new QueryDecomposer() {
            @Override
            public Set<Query> decompose(Query query) {
              return Collections.singleton(query);
            }
          });
    }
    try (Monitor monitor = new Monitor(analyzer, cfg)) {
      monitor.register(new MonitorQuery("q", q));
      HighlightsMatch m = monitor.match(doc(text), HighlightsMatch.MATCHER).matches("q");
      assertNotNull("query must match", m);
      return m;
    }
  }

  static void checkKeepsAllHits(
      String label, Analyzer analyzer, String text, Query q, boolean decompose) throws IOException {
    Set<HighlightsMatch.Hit> ref = reference(analyzer, text, q);
    // Trigger precondition: two distinct reference hits (the message shows that they share
    // positions and differ in offsets).
    assertEquals(label + ": reference hits " + ref, 2, ref.size());
    HighlightsMatch m = monitorMatch(analyzer, text, q, decompose);
    // equals-based copy, so the comparison itself does not use Hit.compareTo
    Set<HighlightsMatch.Hit> got = new HashSet<>(m.getHits(F));
    System.out.println(
        label + ": reference=" + ref + " monitor=" + m.getHits(F) + " hitCount=" + m.getHitCount());
    assertEquals(label + ": HighlightsMatch hits vs Weight#matches", ref, got);
    assertEquals(label + ": getHitCount", ref.size(), m.getHitCount());
  }

  static Query or(Query a, Query b) {
    return new BooleanQuery.Builder()
        .add(a, BooleanClause.Occur.SHOULD)
        .add(b, BooleanClause.Occur.SHOULD)
        .build();
  }

  static Query term(String t) {
    return new TermQuery(new Term(F, t));
  }

  /** A single PrefixQuery over shingles (no decomposition involved): addHit path. */
  @Test
  public void testShinglePrefixQuery() throws IOException {
    checkKeepsAllHits(
        "shingle/prefix", shingleAnalyzer(), "wi fi", new PrefixQuery(new Term(F, "wi")), true);
  }

  /** The candidate's trigger, "wi fi" OR wi, default decomposer: HighlightsMatch.merge path. */
  @Test
  public void testShingleDisjunctionDecomposed() throws IOException {
    checkKeepsAllHits(
        "shingle/or-decomposed", shingleAnalyzer(), "wi fi", or(term("wi fi"), term("wi")), true);
  }

  /** Same query, not decomposed: one Weight#matches call, addHit path. */
  @Test
  public void testShingleDisjunctionNotDecomposed() throws IOException {
    checkKeepsAllHits(
        "shingle/or-single", shingleAnalyzer(), "wi fi", or(term("wi fi"), term("wi")), false);
  }

  /** Multi-word synonym with SynonymGraphFilter + FlattenGraphFilter, guinea OR cavy. */
  @Test
  public void testSynonymGraphDisjunction() throws IOException {
    checkKeepsAllHits(
        "synonymgraph/or-decomposed",
        synonymGraphAnalyzer(),
        "guinea pig",
        or(term("guinea"), term("cavy")),
        true);
  }

  /** getHits() exposes Set&lt;Hit&gt;; java.util.Set#contains is specified in terms of equals. */
  @Test
  public void testHitSetContainsFollowsEquals() throws IOException {
    HighlightsMatch m = monitorMatch(shingleAnalyzer(), "wi fi", term("wi"), true);
    Set<HighlightsMatch.Hit> set = m.getHits().get(F);
    assertNotNull(set);
    // same positions as the real hit 0(0)->0(2), offsets that do not occur
    HighlightsMatch.Hit absent = new HighlightsMatch.Hit(0, 1000, 0, 2000);
    boolean anyEqual = false;
    for (HighlightsMatch.Hit h : set) anyEqual |= h.equals(absent);
    assertFalse("no element equals " + absent + " in " + set, anyEqual);
    assertFalse(
        "Set.contains(" + absent + ") must be false, set=" + set + " (Set contract: equals)",
        set.contains(absent));
  }
}
