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

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.core.FlattenGraphFilter;
import org.apache.lucene.analysis.miscellaneous.KeywordRepeatFilter;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.QueryBuilder;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * ExactPhraseMatcher counts one phrase match per occurrence of the lead term, so when the lead
 * term (or, for MultiPhraseQuery, the lead position's union) has two occurrences at the same
 * position, one phrase occurrence is counted twice. The phrase freq then exceeds the minimum term
 * freq, which is what ExactPhraseMatcher#maxFreq and its merged impacts report as the upper bound,
 * so TOP_SCORES pruning drops the best hit.
 */
public class TestExactPhraseDuplicatePositions extends LuceneTestCase {

  /** Analysis chain from the KeywordRepeatFilter javadoc: index stemmed and unstemmed forms. */
  private static Analyzer keywordRepeatStemAnalyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        WhitespaceTokenizer tok = new WhitespaceTokenizer();
        TokenStream ts = new PorterStemFilter(new KeywordRepeatFilter(tok));
        return new TokenStreamComponents(tok, ts);
      }
    };
  }

  /**
   * "dog barks" is indexed as dog,dog@0 (the stem equals the original) and barks,bark@1, so the
   * phrase "dog bark" has one occurrence there but is counted twice. 1100 other documents "dogs
   * bark" contain the phrase once. IndexSearcher#search(query, 1) must return the best document.
   */
  public void testPhraseQueryKeywordRepeatTopHit() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config(keywordRepeatStemAnalyzer()))) {
        for (int i = 0; i < 1100; i++) {
          Document d = new Document();
          d.add(new TextField("body", "dogs bark", Field.Store.NO));
          w.addDocument(d);
        }
        Document d = new Document();
        d.add(new TextField("body", "dog barks", Field.Store.NO));
        w.addDocument(d);
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        s.setQueryCache(null);
        Query q = new PhraseQuery("body", "dog", "bark");
        TopDocs complete = s.search(q, new TopScoreDocCollectorManager(1, null, Integer.MAX_VALUE));
        TopDocs top = s.search(q, 1); // default: TOP_SCORES once 1000 hits are counted
        assertEquals(
            "search(q, 1) must return the same top hit as COMPLETE scoring: "
                + complete.scoreDocs[0]
                + " vs "
                + top.scoreDocs[0],
            complete.scoreDocs[0].doc,
            top.scoreDocs[0].doc);
      }
    }
  }

  /**
   * Scorer#getMaxScore must be an upper bound of Scorer#score (Scorer javadoc). The merged phrase
   * impacts take the minimum of the term freqs (dog: 2, bark: 1) while the phrase freq is 2.
   */
  public void testPhraseQueryMaxScoreIsUpperBound() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config(keywordRepeatStemAnalyzer()))) {
        // more than one postings block (256 docs), so that block impacts are recorded
        for (int i = 0; i < 300; i++) {
          Document d = new Document();
          d.add(new TextField("body", "dog barks", Field.Store.NO));
          w.addDocument(d);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Query q = s.rewrite(new PhraseQuery("body", "dog", "bark"));
        Weight weight = s.createWeight(q, ScoreMode.TOP_SCORES, 1f);
        LeafReaderContext ctx = r.leaves().get(0);
        Scorer scorer = weight.scorer(ctx);
        int upTo = scorer.advanceShallow(0);
        float maxScore = scorer.getMaxScore(upTo);
        for (int doc = scorer.iterator().nextDoc();
            doc != DocIdSetIterator.NO_MORE_DOCS && doc <= upTo;
            doc = scorer.iterator().nextDoc()) {
          float score = scorer.score();
          assertTrue(
              "doc " + doc + " score " + score + " > getMaxScore(" + upTo + ")=" + maxScore,
              score <= maxScore);
        }
      }
    }
  }

  /** The production default codec (not the test framework's randomized one). */
  private static IndexWriterConfig config(Analyzer analyzer) {
    IndexWriterConfig iwc = analyzer == null ? new IndexWriterConfig() : new IndexWriterConfig(analyzer);
    iwc.setCodec(TestUtil.getDefaultCodec());
    return iwc;
  }

  private static Token tok(String term, int posInc, int start, int end) {
    Token t = new Token(term, start, end);
    t.setPositionIncrement(posInc);
    return t;
  }

  /**
   * MultiPhraseQuery "(b c) a" on a document where b and c are stacked at position 0 (synonyms): the
   * union of b and c reports position 0 twice and the phrase is counted twice. Top hit and Matches.
   */
  public void testMultiPhraseStackedSynonyms() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config(null))) {
        // doc 0: "b a" (one phrase occurrence, b alone at position 0)
        Document d0 = new Document();
        d0.add(new TextField("body", new CannedTokenStream(tok("b", 1, 0, 1), tok("a", 1, 2, 3))));
        w.addDocument(d0);
        // doc 1: b and c stacked at position 0, then a: still one phrase occurrence
        Document d1 = new Document();
        d1.add(
            new TextField(
                "body",
                new CannedTokenStream(tok("b", 1, 0, 1), tok("c", 0, 0, 1), tok("a", 1, 2, 3))));
        w.addDocument(d1);
        // doc 2: mirrored query/doc shape for comparison: a, then b and c stacked at position 1
        Document d2 = new Document();
        d2.add(
            new TextField(
                "body",
                new CannedTokenStream(tok("a", 1, 0, 1), tok("b", 1, 2, 3), tok("c", 0, 2, 3))));
        w.addDocument(d2);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        MultiPhraseQuery bcA =
            new MultiPhraseQuery.Builder()
                .add(new Term[] {new Term("body", "b"), new Term("body", "c")})
                .add(new Term("body", "a"))
                .build();
        MultiPhraseQuery aBc =
            new MultiPhraseQuery.Builder()
                .add(new Term("body", "a"))
                .add(new Term[] {new Term("body", "b"), new Term("body", "c")})
                .build();
        // The phrase occurs once in doc 1 (for "(b c) a") and once in doc 2 (for "a (b c)"). Both
        // documents have length 2 (stacked tokens do not count) and both queries use the terms a, b, c,
        // so equal phrase freqs give equal BM25 scores.
        float f1 = score(s, bcA, 1);
        float f2 = score(s, aBc, 2);
        // count Matches intervals for doc 1
        Weight w = s.createWeight(s.rewrite(bcA), ScoreMode.COMPLETE_NO_SCORES, 1f);
        MatchesIterator it = w.matches(r.leaves().get(0), 1).getMatches("body");
        int intervals = 0;
        StringBuilder sb = new StringBuilder();
        while (it.next()) {
          intervals++;
          sb.append('[').append(it.startPosition()).append(',').append(it.endPosition()).append(']');
        }
        assertTrue(
            "score of '(b c) a' in doc 1 = " + f1 + " differs from score of 'a (b c)' in doc 2 = " + f2
                + " (explain: " + s.explain(bcA, 1).getDetails()[0] + "); Matches intervals for doc 1: " + sb,
            f1 == f2 && intervals == 1);
      }
    }
  }

  private static float score(IndexSearcher s, Query q, int doc) throws IOException {
    Weight w = s.createWeight(s.rewrite(q), ScoreMode.COMPLETE, 1f);
    LeafReaderContext ctx = s.getIndexReader().leaves().get(0);
    Scorer sc = w.scorer(ctx);
    if (sc == null || sc.iterator().advance(doc) != doc) return 0;
    return sc.score();
  }

  /** MultiPhraseQuery with stacked synonyms: TOP_SCORES must return the COMPLETE top hit. */
  public void testMultiPhraseStackedSynonymsTopHit() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config(null))) {
        for (int i = 0; i < 3; i++) {
          // "b a": one phrase occurrence
          Document d = new Document();
          d.add(new TextField("body", new CannedTokenStream(tok("b", 1, 0, 1), tok("a", 1, 2, 3))));
          w.addDocument(d);
        }
        Document d = new Document();
        d.add(
            new TextField(
                "body",
                new CannedTokenStream(tok("b", 1, 0, 1), tok("c", 0, 0, 1), tok("a", 1, 2, 3))));
        w.addDocument(d);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        MultiPhraseQuery q =
            new MultiPhraseQuery.Builder()
                .add(new Term[] {new Term("body", "b"), new Term("body", "c")})
                .add(new Term("body", "a"))
                .build();
        TopDocs complete = s.search(q, new TopScoreDocCollectorManager(1, null, Integer.MAX_VALUE));
        TopDocs top = s.search(q, new TopScoreDocCollectorManager(1, null, 1));
        assertEquals(
            "TOP_SCORES top hit " + top.scoreDocs[0] + " vs COMPLETE " + complete.scoreDocs[0],
            complete.scoreDocs[0].doc,
            top.scoreDocs[0].doc);
      }
    }
  }

  private static SynonymMap dogHound() throws IOException {
    SynonymMap.Builder b = new SynonymMap.Builder(true);
    b.add(new CharsRef("dog"), new CharsRef("hound"), true);
    b.add(new CharsRef("hound"), new CharsRef("dog"), true);
    return b.build();
  }

  /** Same synonym map at index time (flattened) and at query time, a common configuration. */
  private static Analyzer synonymAnalyzer(SynonymMap map, boolean index) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        WhitespaceTokenizer tok = new WhitespaceTokenizer();
        TokenStream ts = new SynonymGraphFilter(tok, map, true);
        if (index) ts = new FlattenGraphFilter(ts);
        return new TokenStreamComponents(tok, ts);
      }
    };
  }

  /**
   * Index-time and query-time synonyms dog/hound. QueryBuilder turns "dog barks" into the
   * MultiPhraseQuery "(dog hound) barks"; every indexed "dog" has "hound" stacked at its position,
   * so each phrase occurrence is counted twice while maxFreq() says 1. The short document "dog
   * barks" scores best, but IndexSearcher#search(q, 1) skips it once 1000 hits are counted.
   */
  public void testIndexAndQueryTimeSynonymsTopHit() throws IOException {
    SynonymMap map = dogHound();
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config(synonymAnalyzer(map, true)))) {
        for (int i = 0; i < 1100; i++) {
          Document d = new Document();
          d.add(new TextField("body", "dog barks loudly", Field.Store.NO));
          w.addDocument(d);
        }
        Document d = new Document();
        d.add(new TextField("body", "dog barks", Field.Store.NO));
        w.addDocument(d);
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        s.setQueryCache(null);
        Query q = new QueryBuilder(synonymAnalyzer(map, false)).createPhraseQuery("body", "dog barks");
        assertTrue(q.toString(), q instanceof MultiPhraseQuery);
        TopDocs complete = s.search(q, new TopScoreDocCollectorManager(1, null, Integer.MAX_VALUE));
        TopDocs top = s.search(q, 1);
        assertEquals(
            q + ": search(q, 1) " + top.scoreDocs[0] + " vs COMPLETE " + complete.scoreDocs[0],
            complete.scoreDocs[0].doc,
            top.scoreDocs[0].doc);
      }
    }
  }
}
