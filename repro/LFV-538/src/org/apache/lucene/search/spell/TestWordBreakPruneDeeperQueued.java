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

import java.util.ArrayList;
import java.util.Arrays;
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
import org.apache.lucene.util.IOUtils;

/**
 * suggestWordBreaks returns early when the suggestion queue is full, assuming that every deeper
 * suggestion has more breaks than everything already queued. The search is depth-first across
 * sibling branches, so an earlier branch may already have queued suggestions with MORE breaks; the
 * later branch's better suggestions are then never generated.
 *
 * <p>The index is the one built by TestWordBreakSpellChecker#setUp (numbers 900..1111 in English
 * plus "thou hast sand betwixt thy toes" etc.).
 */
public class TestWordBreakPruneDeeperQueued extends LuceneTestCase {
  private Directory dir;
  private Analyzer analyzer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir, analyzer);
    for (int i = 900; i < 1112; i++) {
      Document doc = new Document();
      String num = English.intToEnglish(i).replace("-", " ").replace(",", "");
      doc.add(newTextField("numbers", num, Field.Store.NO));
      writer.addDocument(doc);
    }
    for (String s :
        new String[] {
          "thou hast sand betwixt thy toes", "hundredeight eightyeight yeight", "tres y cinco"
        }) {
      Document doc = new Document();
      doc.add(newTextField("numbers", s, Field.Store.NO));
      writer.addDocument(doc);
    }
    writer.commit();
    writer.close();
  }

  @Override
  public void tearDown() throws Exception {
    IOUtils.close(dir, analyzer);
    super.tearDown();
  }

  private static List<String> render(SuggestWord[][] sw) {
    List<String> out = new ArrayList<>();
    for (SuggestWord[] s : sw) {
      StringBuilder b = new StringBuilder();
      int max = 0;
      for (SuggestWord w : s) {
        if (b.length() > 0) b.append(' ');
        b.append(w.string);
        max = Math.max(max, w.freq);
      }
      out.add(b + " (breaks=" + (s.length - 1) + ", maxFreq=" + max + ")");
    }
    return out;
  }

  private static boolean contains(SuggestWord[][] sw, String... words) {
    for (SuggestWord[] s : sw) {
      String[] strings = Arrays.stream(s).map(w -> w.string).toArray(String[]::new);
      if (Arrays.equals(strings, words)) return true;
    }
    return false;
  }

  private static SuggestWord[][] breaks(
      IndexReader ir, String text, int maxChanges, int maxSuggestions, int maxEvaluations)
      throws Exception {
    WordBreakSpellChecker wbsp = new WordBreakSpellChecker();
    wbsp.setMaxChanges(maxChanges);
    wbsp.setMinBreakWordLength(1);
    wbsp.setMinSuggestionFrequency(1);
    wbsp.setMaxEvaluations(maxEvaluations);
    return wbsp.suggestWordBreaks(
        new Term("numbers", text),
        maxSuggestions,
        ir,
        SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX,
        BreakSuggestionSortMethod.NUM_CHANGES_THEN_MAX_FREQUENCY);
  }

  /**
   * Minimal form: index {a, ab, b, c, d, e}, term "abcde", maxChanges=4, maxSuggestions=1. The only
   * splits are a|b|c|d|e (4 breaks) and ab|c|d|e (3 breaks). The branch starting with "a" is
   * explored first and queues the 4-break split, which fills the queue; the branch starting with
   * "ab" then returns before it reaches its 3-break split.
   */
  public void testMinimal() throws Exception {
    try (Directory d = newDirectory()) {
      RandomIndexWriter w = new RandomIndexWriter(random(), d, analyzer);
      Document doc = new Document();
      doc.add(newTextField("f", "a ab b c d e", Field.Store.NO));
      w.addDocument(doc);
      w.close();
      try (IndexReader ir = DirectoryReader.open(d)) {
        WordBreakSpellChecker wbsp = new WordBreakSpellChecker();
        wbsp.setMaxChanges(4);
        SuggestWord[][] sw =
            wbsp.suggestWordBreaks(
                new Term("f", "abcde"),
                1,
                ir,
                SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX,
                BreakSuggestionSortMethod.NUM_CHANGES_THEN_MAX_FREQUENCY);
        assertEquals(render(sw).toString(), 1, sw.length);
        assertTrue(render(sw).toString(), contains(sw, "ab", "c", "d", "e"));
      }
    }
  }

  /**
   * A 4-break suggestion is returned while the 3-break "thousand one hundred eight" is dropped,
   * although suggestions with fewer breaks always sort first.
   */
  public void testFewerBreaksSuggestionDropped() throws Exception {
    try (IndexReader ir = DirectoryReader.open(dir)) {
      // control: with room for 4 suggestions, the 3-break reading is found
      SuggestWord[][] four = breaks(ir, "thousandonehundredeight", 4, 4, 1000);
      assertTrue(render(four).toString(), contains(four, "thousand", "one", "hundred", "eight"));

      for (int maxEvaluations : new int[] {1000 /* default */, Integer.MAX_VALUE}) {
        SuggestWord[][] sw = breaks(ir, "thousandonehundredeight", 4, 3, maxEvaluations);
        assertEquals(render(sw).toString(), 3, sw.length);
        // every candidate with 3 breaks must rank above any candidate with 4 breaks
        assertTrue(
            "maxEvaluations=" + maxEvaluations + " got " + render(sw),
            contains(sw, "thousand", "one", "hundred", "eight"));
        for (SuggestWord[] s : sw) {
          assertTrue("maxEvaluations=" + maxEvaluations + " got " + render(sw), s.length - 1 <= 3);
        }
      }
    }
  }

  /**
   * With maxChanges=3 the dropped suggestion has the same number of breaks but a higher maximum
   * frequency than the one returned in its place.
   */
  public void testHigherFrequencySuggestionDropped() throws Exception {
    try (IndexReader ir = DirectoryReader.open(dir)) {
      int thousand = ir.docFreq(new Term("numbers", "thousand"));
      int nine = ir.docFreq(new Term("numbers", "nine"));
      int hundredeight = ir.docFreq(new Term("numbers", "hundredeight"));
      int sand = ir.docFreq(new Term("numbers", "sand"));
      int thou = ir.docFreq(new Term("numbers", "thou"));
      // precondition: "thousand nine hundred eight" beats "thou sand nine hundredeight" on max freq
      assertTrue(thousand > Math.max(Math.max(thou, sand), Math.max(nine, hundredeight)));

      SuggestWord[][] sw = breaks(ir, "thousandninehundredeight", 3, 2, 1000);
      assertEquals(render(sw).toString(), 2, sw.length);
      assertTrue(render(sw).toString(), contains(sw, "thousand", "nine", "hundredeight"));
      assertTrue(render(sw).toString(), contains(sw, "thousand", "nine", "hundred", "eight"));
    }
  }
}
