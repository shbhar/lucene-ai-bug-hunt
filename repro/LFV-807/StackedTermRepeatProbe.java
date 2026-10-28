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

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.miscellaneous.KeywordRepeatFilter;
import org.apache.lucene.analysis.miscellaneous.RemoveDuplicatesTokenFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.intervals.IntervalIterator;
import org.apache.lucene.queries.intervals.IntervalQuery;
import org.apache.lucene.queries.intervals.Intervals;
import org.apache.lucene.queries.intervals.IntervalsSource;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Does the ordered-repeat overlap also hit a single term when the term is stacked (two tokens with
 * the same text at one position, position increment 0)? Public API only; one document per case;
 * exits non-zero (AssertionError) when any check gives the overlapping answer. Portable from 8.2.0.
 */
public class StackedTermRepeatProbe {

  /** Emits the given terms with the given position increments. */
  static final class Canned extends TokenStream {
    private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posInc =
        addAttribute(PositionIncrementAttribute.class);
    private final String[] terms;
    private final int[] incs;
    private int i;

    Canned(String[] terms, int[] incs) {
      this.terms = terms;
      this.incs = incs;
    }

    @Override
    public boolean incrementToken() {
      if (i == terms.length) {
        return false;
      }
      clearAttributes();
      term.setEmpty().append(terms[i]);
      posInc.setPositionIncrement(incs[i]);
      i++;
      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      i = 0;
    }
  }

  interface StreamFactory {
    TokenStream make() throws IOException;
  }

  static int bad = 0;

  static String describe(StreamFactory f) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (TokenStream ts = f.make()) {
      CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
      PositionIncrementAttribute p = ts.addAttribute(PositionIncrementAttribute.class);
      ts.reset();
      int pos = -1;
      while (ts.incrementToken()) {
        pos += p.getPositionIncrement();
        sb.append(t.toString()).append('@').append(pos).append(' ');
      }
      ts.end();
    }
    return sb.toString().trim();
  }

  static void check(String label, StreamFactory f, IntervalsSource src, int expectedHits)
      throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new TextField("f", f.make()));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Query q = new IntervalQuery("f", src);
        int hits = s.count(q);
        List<String> ivs = new ArrayList<>();
        LeafReaderContext ctx = r.leaves().get(0);
        IntervalIterator it = src.intervals("f", ctx);
        if (it != null && it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          while (it.nextInterval() != IntervalIterator.NO_MORE_INTERVALS) {
            ivs.add("[" + it.start() + "," + it.end() + "]");
          }
        }
        Weight wt = s.createWeight(s.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1f);
        Matches m = wt.matches(ctx, 0);
        StringBuilder ms = new StringBuilder();
        if (m == null) {
          ms.append("null");
        } else {
          MatchesIterator mi = m.getMatches("f");
          while (mi != null && mi.next()) {
            ms.append("[").append(mi.startPosition()).append(",").append(mi.endPosition()).append("]");
          }
        }
        boolean ok = hits == expectedHits;
        if (!ok) {
          bad++;
        }
        System.out.println(
            (ok ? "OK   " : "BAD  ")
                + label
                + " | tokens: "
                + describe(f)
                + " | "
                + src
                + " | expected hits="
                + expectedHits
                + " got hits="
                + hits
                + " intervals="
                + ivs
                + " matches="
                + ms);
      }
    }
  }

  static StreamFactory canned(String[] terms, int[] incs) {
    return () -> new Canned(terms, incs);
  }

  static StreamFactory chain(String text, boolean removeDuplicates) {
    return () -> {
      Tokenizer tok = new WhitespaceTokenizer();
      tok.setReader(new StringReader(text));
      TokenStream ts = new PorterStemFilter(new KeywordRepeatFilter(tok));
      if (removeDuplicates) {
        ts = new RemoveDuplicatesTokenFilter(ts);
      }
      return ts;
    };
  }

  public static void main(String[] args) throws Exception {
    IntervalsSource a = Intervals.term("a");
    IntervalsSource zzz = Intervals.term("zzz");
    StreamFactory stacked = canned(new String[] {"a", "a"}, new int[] {1, 0});
    StreamFactory stackedThenA = canned(new String[] {"a", "a", "a"}, new int[] {1, 0, 1});
    StreamFactory plainAA = canned(new String[] {"a", "a"}, new int[] {1, 1});

    // 1. hand-made stacked token: one position holding "a" twice
    check("1a term(a): postings keep both", stacked, a, 1);
    check("1b ordered(a, a) [bug check]", stacked, Intervals.ordered(a, a), 0);
    check("1c control: ordered(a, or(a, zzz))", stacked, Intervals.ordered(a, Intervals.or(a, zzz)), 0);
    check("1d unorderedNoOverlaps(a, a) [bug check]", stacked, Intervals.unorderedNoOverlaps(a, a), 0);
    check("1e control: ordered(a, a, a) (flat, 3 copies)", stacked, Intervals.ordered(a, a, a), 0);
    // 2. stacked "a" at 0 then "a" at 1: one legitimate match [0,1]; look at the intervals
    check("2a ordered(a, a) (legit hit; see intervals)", stackedThenA, Intervals.ordered(a, a), 1);
    check("2b control: ordered(a, or(a, zzz))", stackedThenA, Intervals.ordered(a, Intervals.or(a, zzz)), 1);
    // 3. a plain two-position document: the documented case still matches
    check("3a sanity: ordered(a, a) on 'a a'", plainAA, Intervals.ordered(a, a), 1);
    // 4. an ordinary analysis chain: KeywordRepeatFilter + PorterStemFilter, stem equals the word
    IntervalsSource cat = Intervals.term("cat");
    check("4a chain 'cat': ordered(cat, cat) [bug check]", chain("cat", false), Intervals.ordered(cat, cat), 0);
    check("4b chain 'cat': control ordered(cat, or(cat, zzz))", chain("cat", false), Intervals.ordered(cat, Intervals.or(cat, zzz)), 0);
    check("4c chain 'cat' + RemoveDuplicatesTokenFilter: ordered(cat, cat)", chain("cat", true), Intervals.ordered(cat, cat), 0);
    check("4d chain 'cats' (stem differs): ordered(cat, cat)", chain("cats", false), Intervals.ordered(cat, cat), 0);
    System.out.println("bad=" + bad);
    if (bad > 0) {
      throw new AssertionError(bad + " check(s) gave the overlapping answer");
    }
  }
}
