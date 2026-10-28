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
import org.apache.lucene.analysis.cjk.CJKBigramFilter;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
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
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Verifier probes: public-API consequences of Hit.compareTo ignoring offsets. */
public class TestHitIdentity extends LuceneTestCase {

  private static List<String> reference(Analyzer a, String text, Query q) throws IOException {
    MemoryIndex mi = new MemoryIndex(true, true);
    mi.addField("f", text, a);
    IndexSearcher s = mi.createSearcher();
    Weight w = s.createWeight(s.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1);
    LeafReaderContext ctx = s.getIndexReader().leaves().get(0);
    Matches m = w.matches(ctx, 0);
    List<String> out = new ArrayList<>();
    MatchesIterator it = m.getMatches("f");
    while (it.next()) {
      out.add(
          new HighlightsMatch.Hit(it.startPosition(), it.startOffset(), it.endPosition(), it.endOffset())
              .toString());
    }
    return out;
  }

  private static HighlightsMatch matchOne(Analyzer a, String text, Query q) throws IOException {
    try (Monitor monitor = new Monitor(a)) {
      monitor.register(new MonitorQuery("q", q));
      Document d = new Document();
      d.add(new TextField("f", text, Field.Store.NO));
      return monitor.match(d, HighlightsMatch.MATCHER).matches("q");
    }
  }

  /** Public API only: two matches on different text compare equal but have different hashCodes. */
  public void testEqualsHashCodeContract() throws IOException {
    Analyzer a = new WhitespaceAnalyzer();
    Query q = new TermQuery(new Term("f", "wi"));
    HighlightsMatch m1 = matchOne(a, "wi", q);
    HighlightsMatch m2 = matchOne(a, "  wi", q);
    System.out.println("V3 equals/hashCode: m1=" + m1 + " m2=" + m2 + " equals=" + m1.equals(m2)
        + " h1=" + m1.hashCode() + " h2=" + m2.hashCode());
    if (m1.equals(m2)) {
      assertEquals("equal objects must have equal hashCodes", m1.hashCode(), m2.hashCode());
    }
  }

  /** CJK bigrams with unigrams and a prefix query: unigram and bigram stacked at one position. */
  public void testCjkBigramPrefix() throws IOException {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new StandardTokenizer();
            return new TokenStreamComponents(t, new CJKBigramFilter(t, CJKBigramFilter.HAN, true));
          }
        };
    String text = "東京";
    Query q = new PrefixQuery(new Term("f", "東"));
    List<String> ref = reference(a, text, q);
    HighlightsMatch hm = matchOne(a, text, q);
    System.out.println("V3 cjk: reference=" + ref + " highlights=" + hm.getHits("f"));
    assertEquals("hits " + ref + " vs " + hm.getHits("f"), ref.size(), hm.getHitCount());
  }

  /** Which of the two stacked hits survives depends on clause order. */
  public void testSurvivorDependsOnClauseOrder() throws IOException {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new WhitespaceTokenizer();
            return new TokenStreamComponents(t, new ShingleFilter(t, 2, 2));
          }
        };
    Query q1 =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("f", "wi")), BooleanClause.Occur.SHOULD)
            .add(new TermQuery(new Term("f", "wi fi")), BooleanClause.Occur.SHOULD)
            .build();
    Query q2 =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("f", "wi fi")), BooleanClause.Occur.SHOULD)
            .add(new TermQuery(new Term("f", "wi")), BooleanClause.Occur.SHOULD)
            .build();
    HighlightsMatch h1 = matchOne(a, "wi fi", q1);
    HighlightsMatch h2 = matchOne(a, "wi fi", q2);
    System.out.println("V3 order: q1=" + q1 + " -> " + h1.getHits("f") + " ; q2=" + q2 + " -> "
        + h2.getHits("f") + " ; ref1=" + reference(a, "wi fi", q1) + " ref2=" + reference(a, "wi fi", q2));
    assertEquals(2, h1.getHitCount());
    assertEquals(2, h2.getHitCount());
  }
}
