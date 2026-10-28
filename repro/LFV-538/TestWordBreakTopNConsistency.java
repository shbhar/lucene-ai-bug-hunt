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
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.spell.WordBreakSpellChecker.BreakSuggestionSortMethod;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check of WordBreakSpellChecker.suggestWordBreaks with maxChanges >= 3.
 *
 * <p>Oracle: the results are ordered by number of breaks, then by max (or summed) component
 * frequency (BreakSuggestionSortMethod). Asking for the top-1 suggestion must therefore return the
 * same suggestion that Lucene itself ranks first when asked for the top-10 (all keys are distinct
 * here, so there are no ties). Only public API is used.
 */
public class TestWordBreakTopNConsistency extends LuceneTestCase {

  private static final String F = "f";

  /** Each string is one document; docFreq(word) = number of docs containing it. */
  private static Directory buildIndex(Analyzer analyzer, String... docs) throws Exception {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      for (String d : docs) {
        Document doc = new Document();
        doc.add(new Field(F, d, org.apache.lucene.document.TextField.TYPE_NOT_STORED));
        w.addDocument(doc);
      }
    }
    return dir;
  }

  private static String render(SuggestWord[][] sw) {
    List<String> out = new ArrayList<>();
    for (SuggestWord[] s : sw) {
      StringBuilder b = new StringBuilder();
      int maxFreq = 0;
      for (SuggestWord w : s) {
        if (b.length() > 0) b.append(' ');
        b.append(w.string);
        maxFreq = Math.max(maxFreq, w.freq);
      }
      out.add(b + " (breaks=" + (s.length - 1) + ", maxFreq=" + maxFreq + ")");
    }
    return out.toString();
  }

  private static String words(SuggestWord[] s) {
    StringBuilder b = new StringBuilder();
    for (SuggestWord w : s) {
      if (b.length() > 0) b.append(' ');
      b.append(w.string);
    }
    return b.toString();
  }

  private static SuggestWord[][] suggest(
      IndexReader ir,
      String text,
      int maxChanges,
      int maxEvaluations,
      int maxSuggestions,
      BreakSuggestionSortMethod sort)
      throws Exception {
    WordBreakSpellChecker wbsc = new WordBreakSpellChecker();
    wbsc.setMinBreakWordLength(1);
    wbsc.setMinSuggestionFrequency(1);
    wbsc.setMaxChanges(maxChanges);
    wbsc.setMaxEvaluations(maxEvaluations);
    return wbsc.suggestWordBreaks(
        new Term(F, text), maxSuggestions, ir, SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX, sort);
  }

  /**
   * Index {p, pq, q, r, s, t}, term "pqrst", maxChanges=4. Two splits exist: "p q r s t" (4 breaks,
   * found first by the recursion) and "pq r s t" (3 breaks). The 3-break split must be the top-1.
   */
  public void testFewerBreaksNotDroppedWhenQueueFull() throws Exception {
    try (Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
        Directory dir = buildIndex(a, "p pq q r s t");
        IndexReader ir = DirectoryReader.open(dir)) {
      List<String> bad = new ArrayList<>();
      for (BreakSuggestionSortMethod sort : BreakSuggestionSortMethod.values()) {
        for (int maxEvals : new int[] {1000, Integer.MAX_VALUE}) {
          SuggestWord[][] top10 = suggest(ir, "pqrst", 4, maxEvals, 10, sort);
          // control: with a large queue Lucene finds both and ranks the 3-break one first
          assertEquals(sort + " maxEvals=" + maxEvals + " " + render(top10), 2, top10.length);
          assertEquals("pq r s t", words(top10[0]));
          assertEquals("p q r s t", words(top10[1]));

          SuggestWord[][] top1 = suggest(ir, "pqrst", 4, maxEvals, 1, sort);
          assertEquals(1, top1.length);
          if (!words(top10[0]).equals(words(top1[0]))) {
            bad.add(sort + " maxEvals=" + maxEvals + " top1=" + render(top1) + " top10=" + render(top10));
          }
        }
      }
      assertTrue("top-1 is not the head of top-10 in " + bad.size() + "/4 cases: " + bad, bad.isEmpty());
    }
  }

  /**
   * Same number of breaks, different frequency (maxChanges=3). Index: "ab" in 3 docs, a, b-less.
   * Term "abcde": splits "a bc d e" (3 breaks, maxFreq 1, sumFreq 4) found first, and "ab c d e" (3
   * breaks, maxFreq 3, sumFreq 6). Both sort methods rank "ab c d e" first.
   */
  public void testHigherFrequencyNotDroppedWhenQueueFull() throws Exception {
    try (Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
        Directory dir = buildIndex(a, "a bc c d e ab", "ab", "ab");
        IndexReader ir = DirectoryReader.open(dir)) {
      assertEquals(3, ir.docFreq(new Term(F, "ab")));
      assertEquals(0, ir.docFreq(new Term(F, "b")));
      List<String> bad = new ArrayList<>();
      for (BreakSuggestionSortMethod sort : BreakSuggestionSortMethod.values()) {
        for (int maxEvals : new int[] {1000, Integer.MAX_VALUE}) {
          SuggestWord[][] top10 = suggest(ir, "abcde", 3, maxEvals, 10, sort);
          assertEquals(sort + " maxEvals=" + maxEvals + " " + render(top10), 2, top10.length);
          assertEquals("ab c d e", words(top10[0]));
          assertEquals("a bc d e", words(top10[1]));

          SuggestWord[][] top1 = suggest(ir, "abcde", 3, maxEvals, 1, sort);
          assertEquals(1, top1.length);
          if (!words(top10[0]).equals(words(top1[0]))) {
            bad.add(sort + " maxEvals=" + maxEvals + " top1=" + render(top1) + " top10=" + render(top10));
          }
        }
      }
      assertTrue("top-1 is not the head of top-10 in " + bad.size() + "/4 cases: " + bad, bad.isEmpty());
    }
  }

  /** Control: with maxChanges=2 the early return is safe; the top-1 matches the top-10 head. */
  public void testControlMaxChangesTwo() throws Exception {
    try (Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
        Directory dir = buildIndex(a, "p pq q r rs s");
        IndexReader ir = DirectoryReader.open(dir)) {
      for (BreakSuggestionSortMethod sort : BreakSuggestionSortMethod.values()) {
        SuggestWord[][] top10 = suggest(ir, "pqrs", 2, 1000, 10, sort);
        SuggestWord[][] top1 = suggest(ir, "pqrs", 2, 1000, 1, sort);
        assertTrue(render(top10), top10.length >= 1);
        assertEquals(render(top1) + " vs " + render(top10), words(top10[0]), words(top1[0]));
      }
    }
  }
}
