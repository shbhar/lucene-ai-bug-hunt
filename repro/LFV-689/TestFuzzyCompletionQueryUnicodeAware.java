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
package org.apache.lucene.search.suggest.document;

import static org.apache.lucene.search.suggest.document.TestSuggestField.iwcWithSuggestField;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.suggest.document.TopSuggestDocs.SuggestScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.automaton.Operations;

/**
 * With unicodeAware=true every letter is one unit, so renaming a letter to another single code
 * point must not change any score. The boost is the number of letters a suggestion's matched prefix
 * shares with the query (as in TestFuzzyCompletionQuery#testFuzzyQuery).
 */
public class TestFuzzyCompletionQueryUnicodeAware extends LuceneTestCase {

  /** Indexes the suggestions, runs a unicode-aware fuzzy query (default settings), key/score. */
  private static List<String> suggest(String key, String[] values, int[] weights) throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    List<String> hits = new ArrayList<>();
    try (Directory dir = newDirectory();
        RandomIndexWriter iw =
            new RandomIndexWriter(random(), dir, iwcWithSuggestField(analyzer, "suggest_field"))) {
      for (int i = 0; i < values.length; i++) {
        Document document = new Document();
        document.add(new SuggestField("suggest_field", values[i], weights[i]));
        iw.addDocument(document);
      }
      try (DirectoryReader reader = iw.getReader()) {
        CompletionQuery query =
            new FuzzyCompletionQuery(
                analyzer,
                new Term("suggest_field", key),
                null,
                FuzzyCompletionQuery.DEFAULT_MAX_EDITS,
                FuzzyCompletionQuery.DEFAULT_TRANSPOSITIONS,
                FuzzyCompletionQuery.DEFAULT_NON_FUZZY_PREFIX,
                FuzzyCompletionQuery.DEFAULT_MIN_FUZZY_LENGTH,
                true, // unicodeAware
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
        TopSuggestDocs suggest = new SuggestIndexSearcher(reader).suggest(query, 10, false);
        for (SuggestScoreDoc hit : suggest.scoreLookupDocs()) {
          hits.add(hit.key + "/" + hit.score);
        }
      }
    }
    return hits;
  }

  public void testAsciiControl() throws Exception {
    // abcd's matched prefix "ab" shares 2 letters: 3 * 2; the typo axcd shares "a": 4 * 1
    assertEquals(
        List.of("abcd/6.0", "axcd/4.0"),
        suggest("abc", new String[] {"abcd", "axcd"}, new int[] {3, 4}));
  }

  public void testOneNonAsciiLetter() throws Exception {
    // the same data with 'b' renamed to 'é' (one code point): the scores must not change
    assertEquals(
        List.of("aécd/6.0", "axcd/4.0"),
        suggest("aéc", new String[] {"aécd", "axcd"}, new int[] {3, 4}));
  }

  public void testAllCyrillicQuery() throws Exception {
    // "kot" over koty (3) and the typo kity (4), written in Cyrillic letters
    assertEquals(
        List.of("коты/6.0", "киты/4.0"),
        suggest("кот", new String[] {"коты", "киты"}, new int[] {3, 4}));
  }
}
