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
 * SpellChecker with SuggestWordFrequencyComparator ("Frequency first, then score"), restricted to a
 * field: asking for k suggestions must return the first k of the full comparator-sorted list. The
 * spell index has at most 7 words and maxHits = 10 * numSug, so every candidate is an n-gram hit
 * and the javadoc's retrieval caveat does not apply. Every score is above the default accuracy 0.5.
 */
public class TestSpellCheckerFrequencyComparatorTopK extends LuceneTestCase {

  /** Six words one edit from "abcdefg" (score 0.857), one document each. */
  private static final String[] RARE = {
    "abcdefx", "abcdefy", "abcdefz", "abcdexg", "abcdeyg", "abcdezg"
  };

  /**
   * User index: {@code frequent} in 50 documents, each {@code rare} word in 1. Returns r[k] =
   * suggestSimilar(query, k) for k = 1..maxK, and r[0] = suggestSimilar(query, 100).
   */
  private static String[][] suggest(
      Comparator<SuggestWord> cmp, String query, int maxK, String frequent, String... rare)
      throws IOException {
    try (Directory userDir = newDirectory();
        Directory spellDir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(userDir, new IndexWriterConfig(null))) {
        for (int i = 0; i < 50 + rare.length; i++) {
          Document d = new Document();
          d.add(new StringField("body", i < 50 ? frequent : rare[i - 50], Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (IndexReader ir = DirectoryReader.open(userDir);
          SpellChecker sc = new SpellChecker(spellDir, new LevenshteinDistance(), cmp)) {
        sc.indexDictionary(new LuceneDictionary(ir, "body"), new IndexWriterConfig(null), false);
        String[][] r = new String[maxK + 1][];
        for (int k = 0; k <= maxK; k++) {
          r[k] =
              sc.suggestSimilar(
                  query, k == 0 ? 100 : k, ir, "body", SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX);
        }
        return r;
      }
    }
  }

  private static void assertTopKIsPrefix(String[][] r, int k) {
    assertEquals(
        "numSug=" + k + " (all: " + Arrays.toString(r[0]) + ")",
        Arrays.toString(Arrays.copyOf(r[0], k)),
        Arrays.toString(r[k]));
  }

  /** receive: 50 docs, score 0.714. relieve: 1 doc, score 0.857. */
  public void testTwoWords() throws IOException {
    Comparator<SuggestWord> cmp = new SuggestWordFrequencyComparator();
    String[][] r = suggest(cmp, "recieve", 2, "receive", "relieve");
    assertEquals("[receive, relieve]", Arrays.toString(r[0])); // control: frequency first
    assertEquals("[receive, relieve]", Arrays.toString(r[2]));
    assertTopKIsPrefix(r, 1); // fails: numSug=1 returns [relieve]
  }

  /** numSug=5, as the javadoc recommends. abcdfeg: 50 docs, score 0.714 (two edits). */
  public void testNumSugFive() throws IOException {
    Comparator<SuggestWord> cmp = new SuggestWordFrequencyComparator();
    String[][] r = suggest(cmp, "abcdefg", 5, "abcdfeg", RARE);
    assertEquals(7, r[0].length); // control: all 7 words found
    assertEquals("abcdfeg", r[0][0]);
    assertTopKIsPrefix(r, 5); // fails: abcdfeg is missing from the 5 suggestions
  }

  /** Control: with the default comparator (score first) both cases are prefix-consistent. */
  public void testDefaultComparator() throws IOException {
    Comparator<SuggestWord> cmp = SuggestWordQueue.DEFAULT_COMPARATOR;
    String[][] a = suggest(cmp, "recieve", 2, "receive", "relieve");
    String[][] b = suggest(cmp, "abcdefg", 7, "abcdfeg", RARE);
    for (int k = 1; k <= 2; k++) {
      assertTopKIsPrefix(a, k);
    }
    for (int k = 1; k <= 7; k++) {
      assertTopKIsPrefix(b, k);
    }
  }
}
