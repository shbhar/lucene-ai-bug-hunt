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
package org.apache.lucene.queries.intervals;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.CharsRefBuilder;
import org.apache.lucene.util.QueryBuilder;

/**
 * Intervals.analyzedText on a token graph (a multi-position token somewhere in the text) drops
 * every stacked single-position synonym that sits outside the multi-position part: only the first
 * token of such a position is kept.
 */
public class TestAnalyzedTextGraphStackedSynonyms extends LuceneTestCase {

  private static final String[] DOCS = {"wi fi dog", "wi fi canine", "wifi dog", "wifi canine"};

  private static Analyzer synonymAnalyzer() throws IOException {
    SynonymMap.Builder b = new SynonymMap.Builder(true);
    CharsRefBuilder scratch = new CharsRefBuilder();
    // multi-word synonym: makes the query token stream a graph (posLength 2 on "wifi")
    b.add(new CharsRef("wifi"), SynonymMap.Builder.join(new String[] {"wi", "fi"}, scratch), true);
    // single-word synonym: "canine" is stacked on "dog" (position increment 0)
    b.add(new CharsRef("dog"), new CharsRef("canine"), true);
    SynonymMap map = b.build();
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new WhitespaceTokenizer();
        return new TokenStreamComponents(t, new SynonymGraphFilter(t, map, true));
      }
    };
  }

  private interface Check {
    void run(IndexSearcher searcher) throws IOException;
  }

  private void withIndex(Check check) throws IOException {
    try (Directory dir = newDirectory()) {
      try (RandomIndexWriter w = new RandomIndexWriter(random(), dir, new WhitespaceAnalyzer())) {
        for (String text : DOCS) {
          Document doc = new Document();
          doc.add(new TextField("f", text, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        check.run(newSearcher(reader));
      }
    }
  }

  /** Through a real analyzer: SynonymGraphFilter with a multi-word and a single-word synonym. */
  public void testSynonymGraphFilter() throws IOException {
    try (Analyzer analyzer = synonymAnalyzer()) {
      withIndex(
          searcher -> {
            // Control 1: QueryBuilder turns the same analysis into a query that matches all 4 docs.
            Query phrase = new QueryBuilder(analyzer).createPhraseQuery("f", "wifi dog");
            assertEquals(phrase.toString(), 4, searcher.count(phrase));
            // Control 2: without the multi-word synonym the stacked synonym is kept: or(canine,dog).
            IntervalsSource dogOnly = Intervals.analyzedText("dog", analyzer, "f", 0, true);
            assertEquals(dogOnly.toString(), 4, searcher.count(new IntervalQuery("f", dogOnly)));

            IntervalsSource source = Intervals.analyzedText("wifi dog", analyzer, "f", 0, true);
            assertEquals(
                "analyzedText(\"wifi dog\") = " + source,
                4,
                searcher.count(new IntervalQuery("f", source)));
          });
    }
  }

  /** Reachable from query text: every term or quoted string of an fn: function is analyzedText. */
  public void testStandardQueryParserIntervalFunction() throws Exception {
    try (Analyzer analyzer = synonymAnalyzer()) {
      withIndex(
          searcher -> {
            Query q;
            try {
              q = new StandardQueryParser(analyzer).parse("fn:or(\"wifi dog\")", "f");
            } catch (Exception e) {
              throw new AssertionError(e);
            }
            assertEquals(q.toString(), 4, searcher.count(q));
          });
    }
  }

  /** The same graph as a canned token stream, for every (maxGaps, ordered) combination. */
  public void testCannedGraphStream() throws IOException {
    withIndex(
        searcher -> {
          for (int maxGaps : new int[] {0, -1, 1}) {
            for (boolean ordered : new boolean[] {true, false}) {
              CannedTokenStream ts =
                  new CannedTokenStream(
                      token("wifi", 1, 2), // multi-position token
                      token("wi", 0, 1),
                      token("fi", 1, 1),
                      token("dog", 1, 1),
                      token("canine", 0, 1)); // stacked synonym of dog
              IntervalsSource source = Intervals.analyzedText(ts, maxGaps, ordered);
              assertEquals(
                  "maxGaps=" + maxGaps + " ordered=" + ordered + " source=" + source,
                  4,
                  searcher.count(new IntervalQuery("f", source)));
            }
          }
        });
  }

  private static Token token(String term, int posInc, int posLength) {
    Token t = new Token(term, 0, term.length());
    t.setPositionIncrement(posInc);
    t.setPositionLength(posLength);
    return t;
  }
}
