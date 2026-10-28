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
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * setMaxChanges: "The maximum numbers of changes (word breaks or combinations) to make on the
 * original term(s)." Only public API is used.
 *
 * <p>Index terms: sun, flower, seed, flowerseed. The query term "sunflower" is not indexed.
 */
public class TestWordBreakMaxChangesBound extends LuceneTestCase {
  private static final String F = "f";
  private Directory dir;
  private Analyzer analyzer;
  private IndexReader reader;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    try (RandomIndexWriter w = new RandomIndexWriter(random(), dir, analyzer)) {
      Document d1 = new Document();
      d1.add(newTextField(F, "sun flower seed", Field.Store.NO));
      w.addDocument(d1);
      Document d2 = new Document();
      d2.add(newTextField(F, "flowerseed", Field.Store.NO));
      w.addDocument(d2);
      w.commit();
    }
    reader = DirectoryReader.open(dir);
  }

  @Override
  public void tearDown() throws Exception {
    IOUtils.close(reader, dir, analyzer);
    super.tearDown();
  }

  private SuggestWord[][] breaks(WordBreakSpellChecker wbsc, String text) throws Exception {
    return wbsc.suggestWordBreaks(
        new Term(F, text),
        10,
        reader,
        SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX,
        BreakSuggestionSortMethod.NUM_CHANGES_THEN_MAX_FREQUENCY);
  }

  private static int maxBreaks(SuggestWord[][] sw) {
    int max = 0;
    for (SuggestWord[] s : sw) {
      max = Math.max(max, s.length - 1);
    }
    return max;
  }

  private static String render(SuggestWord[][] sw) {
    StringBuilder sb = new StringBuilder("[");
    for (SuggestWord[] s : sw) {
      sb.append(" (");
      for (SuggestWord w : s) {
        sb.append(w.string).append(' ');
      }
      sb.append(")");
    }
    return sb.append(" ]").toString();
  }

  /** Control: the default maxChanges=1 yields the one-break split sun + flower. */
  public void testControlDefaultOneBreak() throws Exception {
    WordBreakSpellChecker wbsc = new WordBreakSpellChecker();
    assertEquals(1, wbsc.getMaxChanges());
    SuggestWord[][] sw = breaks(wbsc, "sunflower");
    assertEquals(render(sw), 1, sw.length);
    assertEquals(2, sw[0].length);
    assertEquals("sun", sw[0][0].string);
    assertEquals("flower", sw[0][1].string);
  }

  /** Control: for maxChanges = N >= 1, suggestWordBreaks makes at most N breaks. */
  public void testControlMaxChangesBoundsBreakCount() throws Exception {
    WordBreakSpellChecker wbsc = new WordBreakSpellChecker();
    wbsc.setMaxChanges(1);
    SuggestWord[][] one = breaks(wbsc, "sunflowerseed");
    assertTrue(render(one), one.length > 0);
    assertEquals(render(one), 1, maxBreaks(one));
    wbsc.setMaxChanges(2);
    SuggestWord[][] two = breaks(wbsc, "sunflowerseed");
    assertEquals(render(two), 2, maxBreaks(two));
  }

  /** Control: suggestWordCombinations treats maxChanges = 0 as "no combination". */
  public void testControlCombinationsHonourZero() throws Exception {
    WordBreakSpellChecker wbsc = new WordBreakSpellChecker();
    Term[] terms = {new Term(F, "flower"), new Term(F, "seed")};
    wbsc.setMaxChanges(1);
    assertEquals(
        1, wbsc.suggestWordCombinations(terms, 10, reader, SuggestMode.SUGGEST_ALWAYS).length);
    wbsc.setMaxChanges(0);
    assertEquals(
        0, wbsc.suggestWordCombinations(terms, 10, reader, SuggestMode.SUGGEST_ALWAYS).length);
  }

  /** maxChanges = 0: at most zero word breaks, so no break suggestion. */
  public void testBreaksMaxChangesZero() throws Exception {
    WordBreakSpellChecker wbsc = new WordBreakSpellChecker();
    wbsc.setMaxChanges(0);
    assertEquals(0, wbsc.getMaxChanges());
    SuggestWord[][] sw = breaks(wbsc, "sunflower");
    assertEquals("maxChanges=0 but got " + render(sw), 0, maxBreaks(sw));
  }

  /** maxChanges = -1: same expectation (the setter accepts any int). */
  public void testBreaksMaxChangesNegative() throws Exception {
    WordBreakSpellChecker wbsc = new WordBreakSpellChecker();
    wbsc.setMaxChanges(-1);
    SuggestWord[][] sw = breaks(wbsc, "sunflower");
    assertEquals("maxChanges=-1 but got " + render(sw), 0, maxBreaks(sw));
  }
}
