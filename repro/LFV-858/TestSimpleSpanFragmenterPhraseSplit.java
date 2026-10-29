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
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.CharsRef;

/**
 * SimpleSpanFragmenter "breaks text up into same-size fragments but does not split up Spans". Each
 * test highlights one text with Highlighter, QueryScorer and SimpleSpanFragmenter and checks that
 * the best fragment holds the whole phrase match, which is shorter than the fragment size. Each
 * failing case has a control that differs in one input. Public API only.
 */
public class TestSimpleSpanFragmenterPhraseSplit extends LuceneTestCase {
  private static final String FIELD = "body";
  private static final String NEW_YORK_CITY_HALL = "<B>New</B> <B>York</B> <B>City</B> <B>Hall</B>";
  private static final String YORK_CITY_HALL = "<B>York</B> <B>City</B> <B>Hall</B>";
  private static final String BIG_LAPTOP_BAG = "<B>big</B> <B>laptop</B> <B>bag</B>";

  /** Use {@code new SimpleSpanFragmenter(scorer)}, whose fragment size is 100. */
  private static final int DEFAULT_SIZE = 0;

  // 1. Two phrases that start on the same word: the result depends on the clause order.
  private static final String FLEW = "We flew to New York City Hall last week for a conference";

  public void testSameStartShorterPhraseFirst() throws Exception {
    check(plain(), FLEW, 20, NEW_YORK_CITY_HALL, "new york", "new york city hall");
  }

  /** Control: the same two phrases, the longer one listed first. */
  public void testSameStartLongerPhraseFirst() throws Exception {
    check(plain(), FLEW, 20, NEW_YORK_CITY_HALL, "new york city hall", "new york");
  }

  // 2. The same at the default fragment size (100), in a longer sentence.
  private static final String TRIP =
      "We spent the first morning of our trip walking along the harbour, then took a cab to"
          + " New York City Hall for the opening of the exhibition and stayed there until the evening.";

  public void testSameStartShorterPhraseFirstDefaultSize() throws Exception {
    check(plain(), TRIP, DEFAULT_SIZE, NEW_YORK_CITY_HALL, "new york", "new york city hall");
  }

  /** Control: the same two phrases, the longer one listed first. */
  public void testSameStartLongerPhraseFirstDefaultSize() throws Exception {
    check(plain(), TRIP, DEFAULT_SIZE, NEW_YORK_CITY_HALL, "new york city hall", "new york");
  }

  // 3. A phrase that starts inside another phrase.
  private static final String MUSEUM = "The museum is near New York City Hall and the park";

  public void testOverlappingPhrases() throws Exception {
    check(plain(), MUSEUM, 17, YORK_CITY_HALL, "new york", "york city hall");
  }

  /** Control: the inner phrase alone. */
  public void testInnerPhraseAlone() throws Exception {
    check(plain(), MUSEUM, 17, YORK_CITY_HALL, "york city hall");
  }

  // 4. A phrase whose first words carry stacked synonyms (SynonymGraphFilter, originals kept).
  private static final String BAG = "I left my big laptop bag on the train this morning again";

  public void testPhraseOverStackedSynonyms() throws Exception {
    check(
        synonyms("big", "large", "laptop", "notebook"), BAG, 21, BIG_LAPTOP_BAG, "big laptop bag");
  }

  /** Control: the same phrase without synonyms. */
  public void testSamePhraseWithoutSynonyms() throws Exception {
    check(plain(), BAG, 21, BIG_LAPTOP_BAG, "big laptop bag");
  }

  private static void check(
      Analyzer analyzer, String text, int fragmentSize, String marked, String... phrases)
      throws Exception {
    try (analyzer) {
      Query query = phrases(phrases);
      String best = highlighter(query, fragmentSize).getBestFragment(analyzer, FIELD, text);
      if (best == null || best.contains(marked) == false) {
        // all fragments in text order, for the failure message
        TextFragment[] all =
            highlighter(query, fragmentSize)
                .getBestTextFragments(analyzer.tokenStream(FIELD, text), text, false, 100);
        Arrays.sort(all, Comparator.comparingInt(TextFragment::getFragNum));
        String fragments =
            Arrays.stream(all).map(TextFragment::toString).collect(Collectors.joining(" | "));
        fail(query + ": best fragment [" + best + "], fragments [" + fragments + "]");
      }
    }
  }

  private static Highlighter highlighter(Query query, int fragmentSize) {
    QueryScorer scorer = new QueryScorer(query, FIELD);
    Highlighter highlighter = new Highlighter(scorer);
    highlighter.setTextFragmenter(
        fragmentSize == DEFAULT_SIZE
            ? new SimpleSpanFragmenter(scorer)
            : new SimpleSpanFragmenter(scorer, fragmentSize));
    return highlighter;
  }

  /** One PhraseQuery per phrase; two or more become SHOULD clauses in the given order. */
  private static Query phrases(String... phrases) {
    if (phrases.length == 1) {
      return new PhraseQuery(FIELD, phrases[0].split(" "));
    }
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (String phrase : phrases) {
      builder.add(new PhraseQuery(FIELD, phrase.split(" ")), Occur.SHOULD);
    }
    return builder.build();
  }

  /** StandardTokenizer + LowerCaseFilter. */
  private static Analyzer plain() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new StandardTokenizer();
        return new TokenStreamComponents(tokenizer, new LowerCaseFilter(tokenizer));
      }
    };
  }

  /** plain() + SynonymGraphFilter with one-word rules word -> synonym, keeping the original. */
  private static Analyzer synonyms(String... wordSynonymPairs) throws IOException {
    SynonymMap.Builder builder = new SynonymMap.Builder(true);
    for (int i = 0; i < wordSynonymPairs.length; i += 2) {
      builder.add(new CharsRef(wordSynonymPairs[i]), new CharsRef(wordSynonymPairs[i + 1]), true);
    }
    SynonymMap map = builder.build();
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new StandardTokenizer();
        TokenStream stream = new LowerCaseFilter(tokenizer);
        return new TokenStreamComponents(tokenizer, new SynonymGraphFilter(stream, map, true));
      }
    };
  }
}
