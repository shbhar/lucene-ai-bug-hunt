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

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

/**
 * With a StringDistance other than the internal one, DirectSpellChecker stores the distance score in
 * ScoreTerm.boost and then compares it against FuzzyTermsEnum boosts (internal similarity) to skip
 * "uncompetitive" candidates and to lower the enum's max edits. A better candidate can then never be
 * inspected.
 */
public class TestDirectSpellCheckerCustomDistance extends LuceneTestCase {

  private IndexReader index(Directory dir, String... terms) throws Exception {
    RandomIndexWriter w =
        new RandomIndexWriter(random(), dir, new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false));
    for (String t : terms) {
      Document doc = new Document();
      doc.add(newTextField("f", t, Field.Store.NO));
      w.addDocument(doc);
    }
    IndexReader r = w.getReader();
    w.close();
    return r;
  }

  /** Default settings (numSug=1, maxInspections=5, accuracy 0.5), LevenshteinDistance. */
  public void testDefaultSettingsMissBestCandidate() throws Exception {
    Directory dir = newDirectory();
    // five ed=1 candidates of "abcd" sort before "abcde"; all six have internal similarity 0.75
    // except "abc" (0.67); "abcde" is the best one under LevenshteinDistance (0.8 vs 0.75)
    IndexReader r = index(dir, "abbd", "abc", "abca", "abcb", "abcc", "abcde");
    DirectSpellChecker sc = new DirectSpellChecker();
    StringDistance ld = new LevenshteinDistance();
    sc.setDistance(ld);
    assertEquals(0.8f, ld.getDistance("abcd", "abcde"), 0f);
    assertEquals(0.75f, ld.getDistance("abcd", "abbd"), 0f);
    // the 5 candidates with the highest internal (Damerau-Levenshtein) similarity are abbd, abca, abcb,
    // abcc, abcde (0.75 each; abc has 0.67), so ranking that pool by LevenshteinDistance gives abcde
    SuggestWord[] s = sc.suggestSimilar(new Term("f", "abcd"), 1, r, SuggestMode.SUGGEST_ALWAYS);
    assertEquals(1, s.length);
    assertEquals("abcde", s[0].string);
    r.close();
    dir.close();
  }

  /** maxInspections=1: the returned term is worse than "abcde" under BOTH similarities. */
  public void testReturnedTermWorseUnderBothSimilarities() throws Exception {
    Directory dir = newDirectory();
    IndexReader r = index(dir, "abc", "abcde");
    for (StringDistance sd :
        new StringDistance[] {new LevenshteinDistance(), new JaroWinklerDistance(), new NGramDistance()}) {
      DirectSpellChecker sc = new DirectSpellChecker();
      sc.setDistance(sd);
      sc.setMaxInspections(1);
      // internal similarity: abc = 1 - 1/3 = 0.67, abcde = 1 - 1/4 = 0.75 (abcde is drawn first)
      assertTrue(sd.getDistance("abcd", "abcde") > sd.getDistance("abcd", "abc"));
      SuggestWord[] s = sc.suggestSimilar(new Term("f", "abcd"), 1, r, SuggestMode.SUGGEST_ALWAYS);
      assertEquals(1, s.length);
      assertEquals(sd.getClass().getSimpleName(), "abcde", s[0].string);
    }
    r.close();
    dir.close();
  }

  /** Control: the internal distance returns the best candidate on the same index. */
  public void testInternalDistanceControl() throws Exception {
    Directory dir = newDirectory();
    IndexReader r = index(dir, "abc", "abcde");
    DirectSpellChecker sc = new DirectSpellChecker();
    sc.setMaxInspections(1);
    SuggestWord[] s = sc.suggestSimilar(new Term("f", "abcd"), 1, r, SuggestMode.SUGGEST_ALWAYS);
    assertEquals(1, s.length);
    assertEquals("abcde", s[0].string);
    r.close();
    dir.close();
  }
}
