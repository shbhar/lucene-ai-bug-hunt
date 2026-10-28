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
package org.apache.lucene.verify.v1;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.core.FlattenGraphFilter;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.CharsRefBuilder;
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
import org.apache.lucene.monitor.HighlightsMatch;
import org.apache.lucene.monitor.HighlightsMatch.Hit;
import org.apache.lucene.monitor.MatchingQueries;
import org.apache.lucene.monitor.Monitor;
import org.apache.lucene.monitor.MonitorConfiguration;
import org.apache.lucene.monitor.MonitorQuery;
import org.apache.lucene.monitor.QueryDecomposer;
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
import org.apache.lucene.tests.analysis.MockSynonymAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check (public API only; this class is deliberately outside the monitor package):
 * HighlightsMatch must keep every distinct hit that Weight#matches reports, including two hits
 * that share start/end positions but have different offsets (stacked tokens).
 *
 * <p>Reference: Weight#matches on a plain IndexWriter index whose field indexes offsets.
 */
public class TestHitOffsetsV1 extends LuceneTestCase {

  private static final String F = "f";

  /** Whitespace tokens plus 2-word shingles, unigrams kept ("wi", "wi fi" stacked at position 0). */
  private static Analyzer shingleAnalyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new WhitespaceTokenizer();
        ShingleFilter sf = new ShingleFilter(t, 2, 2);
        sf.setOutputUnigrams(true);
        return new TokenStreamComponents(t, sf);
      }
    };
  }

  /** Index-time multi-word synonym "guinea pig" => "cavy" (original kept), flattened for indexing. */
  private static Analyzer synonymGraphAnalyzer() throws IOException {
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

  /** Every (startPos, startOff, endPos, endOff) tuple that Weight#matches reports for doc 0. */
  private static Set<Hit> reference(Analyzer analyzer, String text, Query q) throws IOException {
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    ft.freeze();
    Set<Hit> out = new HashSet<>();
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      Document d = new Document();
      d.add(new Field(F, text, ft));
      w.addDocument(d);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        Weight wt = s.createWeight(s.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1);
        for (LeafReaderContext ctx : r.leaves()) {
          for (int doc = 0; doc < ctx.reader().maxDoc(); doc++) {
            Matches m = wt.matches(ctx, doc);
            if (m == null) continue;
            MatchesIterator mi = m.getMatches(F);
            if (mi == null) continue;
            while (mi.next()) {
              out.add(new Hit(mi.startPosition(), mi.startOffset(), mi.endPosition(), mi.endOffset()));
            }
          }
        }
      }
    }
    return out;
  }

  private static HighlightsMatch monitorMatch(
      Analyzer analyzer, String text, Query q, boolean decompose) throws IOException {
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
      Document doc = new Document();
      doc.add(new TextField(F, text, Field.Store.NO));
      MatchingQueries<HighlightsMatch> mq = monitor.match(doc, HighlightsMatch.MATCHER);
      HighlightsMatch m = mq.matches("q");
      assertNotNull("query must match", m);
      return m;
    }
  }

  private static void checkKeepsAllHits(
      String label, Analyzer analyzer, String text, Query q, boolean decompose)
      throws IOException {
    Set<Hit> ref = reference(analyzer, text, q);
    // Precondition (the trigger): the reference has 2 distinct hits, so they differ in offsets only
    // if their positions are equal; the message shows the tuples.
    assertEquals(label + ": reference hits " + ref, 2, ref.size());
    HighlightsMatch m = monitorMatch(analyzer, text, q, decompose);
    // equals-based copy (HashSet), so the comparison does not rely on Hit.compareTo
    Set<Hit> got = new HashSet<>(m.getHits(F));
    System.out.println(label + ": reference=" + ref + " monitor=" + got + " hitCount=" + m.getHitCount());
    assertEquals(label + ": HighlightsMatch hits vs Weight#matches", ref, got);
    assertEquals(label + ": getHitCount", ref.size(), m.getHitCount());
  }

  private static Query guineaOrCavy() {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term(F, "guinea")), BooleanClause.Occur.SHOULD)
        .add(new TermQuery(new Term(F, "cavy")), BooleanClause.Occur.SHOULD)
        .build();
  }

  /** Multi-word synonym "cavy" stacked on "guinea"; default decomposer -> HighlightsMatch.merge path. */
  public void testSynonymDisjunctionDecomposed() throws IOException {
    checkKeepsAllHits("synonym/decomposed", new MockSynonymAnalyzer(), "guinea pig", guineaOrCavy(), true);
  }

  /** Same, query not decomposed -> single Weight#matches -> addHit path. */
  public void testSynonymDisjunctionNotDecomposed() throws IOException {
    checkKeepsAllHits("synonym/single", new MockSynonymAnalyzer(), "guinea pig", guineaOrCavy(), false);
  }

  /** A single PrefixQuery over shingles: "wi" (0-2) and "wi fi" (0-5) both at position 0. */
  public void testShinglePrefixQuery() throws IOException {
    checkKeepsAllHits(
        "shingle/prefix", shingleAnalyzer(), "wi fi", new PrefixQuery(new Term(F, "wi")), true);
  }

  /** Real SynonymGraphFilter + FlattenGraphFilter, default decomposer. */
  public void testSynonymGraphFilterDisjunction() throws IOException {
    checkKeepsAllHits("synonymgraph/decomposed", synonymGraphAnalyzer(), "guinea pig", guineaOrCavy(), true);
  }

  /** getHits() exposes Set<Hit>; Set.contains is specified in terms of equals. */
  public void testHitSetContainsFollowsEquals() throws IOException {
    HighlightsMatch m =
        monitorMatch(new MockSynonymAnalyzer(), "guinea pig", new TermQuery(new Term(F, "guinea")), true);
    Set<Hit> set = m.getHits().get(F);
    assertNotNull(set);
    Hit absent = new Hit(0, 1000, 0, 2000); // same positions as the real hit, offsets that do not exist
    boolean anyEqual = false;
    for (Hit h : set) anyEqual |= h.equals(absent);
    assertFalse("no element equals " + absent + " in " + set, anyEqual);
    assertFalse(
        "Set.contains(" + absent + ") must be false, set=" + set + " (Set contract: equals)",
        set.contains(absent));
  }
}
