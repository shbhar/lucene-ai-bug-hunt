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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DirectSpellChecker with a non-default StringDistance.
 *
 * <p>setDistance javadoc: "this spellchecker draws its candidates from the term dictionary using
 * Damerau-Levenshtein ... increasing setMaxInspections to draw more candidates for your metric to
 * rank". ScoreTerm.boost javadoc: "the similarity from the FuzzyTermsEnum (internal similarity
 * score)".
 *
 * <p>Index {lucen, lucenes}, query "lucene". "lucenes" has the higher internal similarity (0.833
 * vs 0.8) and the higher score under LevenshteinDistance, JaroWinklerDistance and NGramDistance.
 * With numSug=1 and maxInspections=1 it is the only candidate the pool can hold, and it is the best
 * candidate under every metric, so it must be the suggestion.
 */
public class TestDirectSpellCheckerDistancePruning extends LuceneTestCase {

  private static final String FIELD = "f";

  private static IndexReader index(Directory dir, String... words) throws IOException {
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    for (String word : words) {
      Document doc = new Document();
      doc.add(new StringField(FIELD, word, Field.Store.NO));
      w.addDocument(doc);
    }
    IndexReader r = w.getReader();
    w.close();
    return r;
  }

  private static String top(DirectSpellChecker sc, String query, int numSug, IndexReader r)
      throws IOException {
    SuggestWord[] s = sc.suggestSimilar(new Term(FIELD, query), numSug, r);
    return s.length == 0 ? "<none>" : s[0].string;
  }

  /** maxInspections=1: the better candidate (under both similarities) is never inspected. */
  public void testCustomDistanceMissesDominatingCandidate() throws Exception {
    String query = "lucene";
    String worse = "lucen";
    String better = "lucenes";
    StringDistance internal = DirectSpellChecker.INTERNAL_LEVENSHTEIN;
    // precondition: internal similarity prefers 'better'
    assertTrue(internal.getDistance(query, better) > internal.getDistance(query, worse));

    StringDistance[] distances = {
      new LevenshteinDistance(), new JaroWinklerDistance(), new NGramDistance()
    };
    List<String> wrong = new ArrayList<>();
    try (Directory dir = newDirectory();
        IndexReader r = index(dir, worse, better)) {
      for (StringDistance d : distances) {
        // precondition: the configured metric prefers 'better' too
        assertTrue(
            d + " must prefer " + better, d.getDistance(query, better) > d.getDistance(query, worse));
        DirectSpellChecker sc = new DirectSpellChecker();
        sc.setDistance(d);
        sc.setMaxInspections(1);
        String got = top(sc, query, 1, r);
        if (better.equals(got) == false) {
          wrong.add(
              d.getClass().getSimpleName()
                  + " returned "
                  + got
                  + " ("
                  + d.getDistance(query, got)
                  + ") instead of "
                  + better
                  + " ("
                  + d.getDistance(query, better)
                  + ")");
        }
      }
    }
    assertTrue("wrong suggestions: " + wrong, wrong.isEmpty());
  }

  /**
   * Default settings (numSug=1, maxInspections=5), LevenshteinDistance. Index: five ed=1 neighbours
   * of "lucene" that sort before "lucenes", plus "lucenes". Internal similarities: lucen 0.8, the
   * four substitutions 0.833, lucenes 0.833. LevenshteinDistance: lucenes 0.857, all others 0.833.
   * The documented pool (top 5 by internal similarity) contains lucenes, which the metric ranks
   * first.
   */
  public void testCustomDistanceDefaultSettings() throws Exception {
    String query = "lucene";
    String[] words = {"lucen", "lucena", "lucenb", "lucenc", "lucend", "lucenes"};
    StringDistance lev = new LevenshteinDistance();
    StringDistance internal = DirectSpellChecker.INTERNAL_LEVENSHTEIN;
    for (int i = 0; i < words.length - 1; i++) {
      // precondition: lucenes is at least as good as every other term under internal similarity
      // and strictly better under LevenshteinDistance
      assertTrue(internal.getDistance(query, "lucenes") >= internal.getDistance(query, words[i]));
      assertTrue(lev.getDistance(query, "lucenes") > lev.getDistance(query, words[i]));
    }
    // precondition: lucen has strictly the lowest internal similarity, so it is the one term the
    // documented pool of 5 leaves out
    assertTrue(internal.getDistance(query, "lucen") < internal.getDistance(query, "lucena"));
    try (Directory dir = newDirectory();
        IndexReader r = index(dir, words)) {
      DirectSpellChecker sc = new DirectSpellChecker();
      sc.setDistance(lev);
      assertEquals(5, sc.getMaxInspections());
      assertEquals("lucenes", top(sc, query, 1, r));
    }
  }

  /** Controls: the internal distance and a separate LuceneLevenshteinDistance instance. */
  public void testControlInternalScale() throws Exception {
    try (Directory dir = newDirectory();
        IndexReader r = index(dir, "lucen", "lucenes")) {
      for (StringDistance d :
          new StringDistance[] {
            DirectSpellChecker.INTERNAL_LEVENSHTEIN, new LuceneLevenshteinDistance()
          }) {
        DirectSpellChecker sc = new DirectSpellChecker();
        sc.setDistance(d);
        sc.setMaxInspections(1);
        assertEquals(String.valueOf(d), "lucenes", top(sc, "lucene", 1, r));
      }
    }
    try (Directory dir = newDirectory();
        IndexReader r = index(dir, "lucen", "lucena", "lucenb", "lucenc", "lucend", "lucenes")) {
      DirectSpellChecker sc = new DirectSpellChecker();
      // internal: lucena..lucend and lucenes all tie at 0.833; the comparator breaks ties by term
      // text, so the expected top is lucena
      assertEquals("lucena", top(sc, "lucene", 1, r));
    }
  }
}
