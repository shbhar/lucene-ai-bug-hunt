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
package org.apache.lucene.search.spell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.spell.WordBreakSpellChecker.BreakSuggestionSortMethod;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.English;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * suggestWordBreaks stops early once its suggestion queue is full, on the assumption that every
 * split it has not generated yet has more word breaks than every queued split. The search is
 * depth-first across sibling splits, so with maxChanges >= 3 an earlier sibling can fill the queue
 * first, and a later sibling's better split is never generated.
 */
public class TestWordBreakSpellCheckerFullQueue extends LuceneTestCase {

  private static final String FIELD = "f";

  private Directory index(String... docs) throws IOException {
    Directory dir = newDirectory();
    try (Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
        RandomIndexWriter writer = new RandomIndexWriter(random(), dir, analyzer)) {
      for (String text : docs) {
        Document doc = new Document();
        doc.add(newTextField(FIELD, text, Field.Store.NO));
        writer.addDocument(doc);
      }
    }
    return dir;
  }

  /** The splits of {@code text}, best first, each as its words joined by single spaces. */
  private static List<String> breaks(
      IndexReader reader,
      String text,
      int maxChanges,
      int maxSuggestions,
      BreakSuggestionSortMethod sortMethod,
      int maxEvaluations)
      throws IOException {
    WordBreakSpellChecker spellChecker = new WordBreakSpellChecker();
    spellChecker.setMaxChanges(maxChanges);
    spellChecker.setMaxEvaluations(maxEvaluations);
    List<String> splits = new ArrayList<>();
    for (SuggestWord[] split :
        spellChecker.suggestWordBreaks(
            new Term(FIELD, text),
            maxSuggestions,
            reader,
            SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX,
            sortMethod)) {
      List<String> words = new ArrayList<>();
      for (SuggestWord word : split) {
        words.add(word.string);
      }
      splits.add(String.join(" ", words));
    }
    return splits;
  }

  /** maxChanges=4: the 4-break split is returned and the 3-break split is lost. */
  public void testFewerBreaksLost() throws IOException {
    try (Directory dir = index("a ab b c d e");
        IndexReader reader = DirectoryReader.open(dir)) {
      for (BreakSuggestionSortMethod sort : BreakSuggestionSortMethod.values()) {
        for (int maxEvaluations : new int[] {1000, Integer.MAX_VALUE}) {
          String config = sort + ", maxEvaluations=" + maxEvaluations;
          // Lucene's own ranking when both splits fit: fewer breaks first
          assertEquals(
              config,
              List.of("ab c d e", "a b c d e"),
              breaks(reader, "abcde", 4, 2, sort, maxEvaluations));
          // with maxChanges=3 the best split is returned
          assertEquals(
              config, List.of("ab c d e"), breaks(reader, "abcde", 3, 1, sort, maxEvaluations));
          // raising maxChanges to 4 must not make it worse
          assertEquals(
              config, List.of("ab c d e"), breaks(reader, "abcde", 4, 1, sort, maxEvaluations));
        }
      }
    }
  }

  /** maxChanges=3: a split with the same 3 breaks but lower frequencies is returned instead. */
  public void testHigherFrequencyLost() throws IOException {
    // "ab" is in 3 documents, every other word in 1; there is no "b"
    try (Directory dir = index("a bc c d e ab", "ab", "ab");
        IndexReader reader = DirectoryReader.open(dir)) {
      for (BreakSuggestionSortMethod sort : BreakSuggestionSortMethod.values()) {
        assertEquals(
            sort.toString(),
            List.of("ab c d e", "a bc d e"),
            breaks(reader, "abcde", 3, 2, sort, 1000));
        assertEquals(
            sort.toString(), List.of("ab c d e"), breaks(reader, "abcde", 3, 1, sort, 1000));
      }
    }
  }

  /** The same on the number words that TestWordBreakSpellChecker indexes. */
  public void testNumberWords() throws IOException {
    List<String> docs = new ArrayList<>();
    for (int i = 900; i < 1112; i++) {
      docs.add(English.intToEnglish(i).replace("-", " ").replace(",", ""));
    }
    docs.add("thou hast sand betwixt thy toes");
    docs.add("hundredeight eightyeight yeight");
    docs.add("tres y cinco");
    BreakSuggestionSortMethod max = BreakSuggestionSortMethod.NUM_CHANGES_THEN_MAX_FREQUENCY;
    try (Directory dir = index(docs.toArray(new String[0]));
        IndexReader reader = DirectoryReader.open(dir)) {
      // maxChanges=4: the top 4 hold one 2-break, two 3-break and one 4-break split, so the top 3
      // must be the 2-break and both 3-break splits; instead the 4-break "thou sand one hundred
      // eight" takes the place of the 3-break "thousand one hundred eight"
      List<String> top4 = breaks(reader, "thousandonehundredeight", 4, 4, max, 1000);
      assertTrue(top4.toString(), top4.contains("thousand one hundred eight"));
      List<String> top3 = breaks(reader, "thousandonehundredeight", 4, 3, max, 1000);
      assertTrue("top 3 " + top3 + ", top 4 " + top4, top3.contains("thousand one hundred eight"));
      // maxChanges=3: "thou sand nine hundredeight" (highest docFreq 110) is returned in place of
      // "thousand nine hundred eight" (highest docFreq 112)
      assertEquals(
          List.of("thousand nine hundredeight", "thousand nine hundred eight"),
          breaks(reader, "thousandninehundredeight", 3, 2, max, 1000));
    }
  }
}
