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
import java.util.Arrays;
import java.util.Comparator;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check (public API only): with a {@link SuggestWordFrequencyComparator}, the top-k
 * returned by {@link SpellChecker#suggestSimilar(String, int, IndexReader, String, SuggestMode)}
 * must be the first k entries of the complete, comparator-sorted candidate list, as long as every
 * spell-index word is an n-gram hit (here the spell index has at most 6 words and maxHits = 10 *
 * numSug >= 20), so the javadoc's n-gram retrieval caveat cannot apply.
 *
 * <p>Words (query "abcdefgh", not in the user index), Levenshtein score / docFreq:
 *
 * <ul>
 *   <li>"abcdefghijkl": 1 - 4/12 = 0.667, freq 10 (strongest n-gram hit)
 *   <li>"abcdefgw".."abcdefgz": 1 - 1/8 = 0.875, freq 1 (strong n-gram hits)
 *   <li>"abxdefyh": 1 - 2/8 = 0.75, freq 5 (weakest n-gram hit: shares only "def")
 * </ul>
 *
 * All scores are above the default accuracy 0.5.
 */
public class TestFrequencyComparatorTopKConsistency extends LuceneTestCase {
  private static final String FIELD = "body";
  private static final String QUERY = "abcdefgh";

  private Directory userDir;
  private Directory spellDir;
  private IndexReader userReader;
  private SpellChecker spell;

  private void build(Comparator<SuggestWord> cmp, Object... wordAndFreq) throws IOException {
    userDir = newDirectory();
    try (IndexWriter w = new IndexWriter(userDir, new IndexWriterConfig(null))) {
      for (int i = 0; i < wordAndFreq.length; i += 2) {
        String word = (String) wordAndFreq[i];
        int freq = (Integer) wordAndFreq[i + 1];
        for (int j = 0; j < freq; j++) {
          Document d = new Document();
          d.add(new StringField(FIELD, word, Field.Store.NO));
          w.addDocument(d);
        }
      }
    }
    userReader = DirectoryReader.open(userDir);
    spellDir = newDirectory();
    spell = new SpellChecker(spellDir, new LevenshteinDistance(), cmp);
    spell.indexDictionary(
        new LuceneDictionary(userReader, FIELD), new IndexWriterConfig(null), false);
  }

  private void closeAll() throws IOException {
    if (spell != null) spell.close();
    if (userReader != null) userReader.close();
    if (spellDir != null) spellDir.close();
    if (userDir != null) userDir.close();
    spell = null;
    userReader = null;
    spellDir = null;
    userDir = null;
  }

  private String[] suggest(int numSug, SuggestMode mode) throws IOException {
    String[] r = spell.suggestSimilar(QUERY, numSug, userReader, FIELD, mode);
    System.out.println(
        "comparator="
            + spell.getComparator().getClass().getSimpleName()
            + " mode="
            + mode
            + " numSug="
            + numSug
            + " -> "
            + Arrays.toString(r));
    return r;
  }

  private static void printScores(String... words) {
    LevenshteinDistance ld = new LevenshteinDistance();
    for (String w : words) {
      System.out.println("  score(" + QUERY + ", " + w + ") = " + ld.getDistance(QUERY, w));
    }
  }

  /** Minimal case: numSug = 2, three candidates. */
  public void testFrequencyComparatorNumSugTwo() throws Exception {
    build(
        new SuggestWordFrequencyComparator(),
        "abcdefghijkl", 10,
        "abcdefgx", 1,
        "abxdefyh", 5);
    try {
      printScores("abcdefghijkl", "abcdefgx", "abxdefyh");
      // Control: all three words are retrieved and pass accuracy/freq filters; complete sorted list.
      String[] all = suggest(3, SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX);
      assertArrayEquals(new String[] {"abcdefghijkl", "abxdefyh", "abcdefgx"}, all);
      // Top-2 under "frequency first, then score" must be the first 2 of the complete list.
      String[] top2 = suggest(2, SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX);
      assertEquals(
          "top-2 is not a prefix of the complete comparator-sorted list",
          Arrays.toString(Arrays.copyOf(all, 2)),
          Arrays.toString(top2));
    } finally {
      closeAll();
    }
  }

  /** numSug = 5 (the javadoc's recommended minimum), six candidates. */
  public void testFrequencyComparatorNumSugFive() throws Exception {
    build(
        new SuggestWordFrequencyComparator(),
        "abcdefghijkl", 10,
        "abcdefgw", 1,
        "abcdefgx", 1,
        "abcdefgy", 1,
        "abcdefgz", 1,
        "abxdefyh", 5);
    try {
      String[] all = suggest(100, SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX);
      assertEquals("control: every word must be suggested with a large numSug", 6, all.length);
      assertEquals("abxdefyh", all[1]);
      String[] top5 = suggest(5, SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX);
      assertEquals(
          "top-5 is not a prefix of the complete comparator-sorted list",
          Arrays.toString(Arrays.copyOf(all, 5)),
          Arrays.toString(top5));
    } finally {
      closeAll();
    }
  }

  /** Same as the minimal case, in SUGGEST_MORE_POPULAR mode (query word has freq 1). */
  public void testFrequencyComparatorMorePopular() throws Exception {
    build(
        new SuggestWordFrequencyComparator(),
        QUERY, 1,
        "abcdefghijkl", 10,
        "abcdefgx", 1,
        "abxdefyh", 5);
    try {
      String[] all = suggest(3, SuggestMode.SUGGEST_MORE_POPULAR);
      assertArrayEquals(new String[] {"abcdefghijkl", "abxdefyh", "abcdefgx"}, all);
      String[] top2 = suggest(2, SuggestMode.SUGGEST_MORE_POPULAR);
      assertEquals(
          "top-2 is not a prefix of the complete comparator-sorted list",
          Arrays.toString(Arrays.copyOf(all, 2)),
          Arrays.toString(top2));
    } finally {
      closeAll();
    }
  }

  /** Control: the same prefix oracle holds with the default (score-first) comparator. */
  public void testDefaultComparatorControl() throws Exception {
    build(
        SuggestWordQueue.DEFAULT_COMPARATOR,
        "abcdefghijkl", 10,
        "abcdefgw", 1,
        "abcdefgx", 1,
        "abcdefgy", 1,
        "abcdefgz", 1,
        "abxdefyh", 5);
    try {
      String[] all = suggest(100, SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX);
      assertEquals(6, all.length);
      for (int k = 1; k <= 6; k++) {
        String[] topK = suggest(k, SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX);
        assertEquals(
            "default comparator top-" + k,
            Arrays.toString(Arrays.copyOf(all, k)),
            Arrays.toString(topK));
      }
    } finally {
      closeAll();
    }
  }
}
