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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * HighlightsMatch keeps its hits in a TreeSet, and HighlightsMatch.Hit.compareTo compares start and
 * end positions only, while Hit.equals (a record) also compares offsets. Two different hits at the
 * same positions with different offsets (stacked tokens, here from ShingleFilter: "wi fi" gives
 * the unigram "wi" [0,2) and the shingle "wi fi" [0,5), both at position 0) collapse into one, so
 * getHits and getHitCount lose a hit that Weight#matches reports. Uses only public API.
 */
public class TestHighlightsMatchHitOrdering extends LuceneTestCase {

  private static Analyzer analyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new WhitespaceTokenizer();
        return new TokenStreamComponents(t, new ShingleFilter(t, 2, 2));
      }
    };
  }

  private static List<HighlightsMatch.Hit> referenceHits(Analyzer a, String text, Query q)
      throws IOException {
    MemoryIndex mi = new MemoryIndex(true, true);
    mi.addField("f", text, a);
    IndexSearcher s = mi.createSearcher();
    Weight w = s.createWeight(s.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1);
    LeafReaderContext ctx = s.getIndexReader().leaves().get(0);
    Matches m = w.matches(ctx, 0);
    List<HighlightsMatch.Hit> out = new ArrayList<>();
    MatchesIterator mi2 = m.getMatches("f");
    while (mi2.next()) {
      out.add(
          new HighlightsMatch.Hit(
              mi2.startPosition(), mi2.startOffset(), mi2.endPosition(), mi2.endOffset()));
    }
    return out;
  }

  public void testStackedTokensWithDifferentOffsets() throws IOException {
    Analyzer a = analyzer();
    String text = "wi fi";
    Query q =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("f", "wi fi")), BooleanClause.Occur.SHOULD)
            .add(new TermQuery(new Term("f", "wi")), BooleanClause.Occur.SHOULD)
            .build();
    List<HighlightsMatch.Hit> reference = referenceHits(a, text, q);
    assertEquals("Weight#matches reports two hits: " + reference, 2, reference.size());
    assertEquals(reference.get(0).startPosition(), reference.get(1).startPosition());
    assertEquals(reference.get(0).endPosition(), reference.get(1).endPosition());
    assertNotEquals(reference.get(0), reference.get(1));

    try (Monitor monitor = new Monitor(a)) {
      monitor.register(new MonitorQuery("q", q));
      Document d = new Document();
      d.add(new TextField("f", text, Field.Store.NO));
      HighlightsMatch hm = monitor.match(d, HighlightsMatch.MATCHER).matches("q");
      assertNotNull(hm);
      assertEquals(
          "HighlightsMatch should keep both hits " + reference + ", got " + hm.getHits("f"),
          2,
          hm.getHitCount());
    }
  }
}
