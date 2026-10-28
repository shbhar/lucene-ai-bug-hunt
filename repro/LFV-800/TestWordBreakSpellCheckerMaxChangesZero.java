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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.spell.WordBreakSpellChecker.BreakSuggestionSortMethod;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * setMaxChanges: "The maximum numbers of changes (word breaks or combinations) to make on the
 * original term(s)". With a maximum of 0, suggestWordCombinations makes no change, but
 * suggestWordBreaks still returns a one-break suggestion.
 */
public class TestWordBreakSpellCheckerMaxChangesZero extends LuceneTestCase {
  private static final String F = "f";

  private Directory dir;
  private IndexReader reader;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      for (String word : new String[] {"sun", "flower", "seed", "flowerseed"}) {
        doc.add(new StringField(F, word, Field.Store.NO));
      }
      w.addDocument(doc);
    }
    reader = DirectoryReader.open(dir);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  /** Break suggestions for "sunflower" (not in the index), each as "words (score)". */
  private List<String> breakSunflower(WordBreakSpellChecker wb) throws IOException {
    SuggestWord[][] suggestions =
        wb.suggestWordBreaks(
            new Term(F, "sunflower"),
            10,
            reader,
            SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX,
            BreakSuggestionSortMethod.NUM_CHANGES_THEN_MAX_FREQUENCY);
    List<String> out = new ArrayList<>();
    for (SuggestWord[] words : suggestions) {
      List<String> parts = new ArrayList<>();
      for (SuggestWord word : words) {
        parts.add(word.string);
      }
      out.add(String.join(" ", parts) + " (" + words[0].score + ")");
    }
    return out;
  }

  /** Combination suggestions for the two terms "flower" "seed". */
  private int combineFlowerSeed(WordBreakSpellChecker wb) throws IOException {
    Term[] terms = {new Term(F, "flower"), new Term(F, "seed")};
    return wb.suggestWordCombinations(terms, 10, reader, SuggestMode.SUGGEST_ALWAYS).length;
  }

  /** Control: the default maximum of 1 allows one break and one combination. */
  public void testDefaultAllowsOneChange() throws IOException {
    WordBreakSpellChecker wb = new WordBreakSpellChecker();
    assertEquals(1, wb.getMaxChanges());
    assertEquals(List.of("sun flower (1.0)"), breakSunflower(wb));
    assertEquals(1, combineFlowerSeed(wb));
  }

  /** Control: suggestWordCombinations honours a maximum of 0. */
  public void testCombinationsHonourZero() throws IOException {
    WordBreakSpellChecker wb = new WordBreakSpellChecker();
    wb.setMaxChanges(0);
    assertEquals(0, combineFlowerSeed(wb));
  }

  /** A maximum of 0 changes allows no word break. */
  public void testBreaksHonourZero() throws IOException {
    WordBreakSpellChecker wb = new WordBreakSpellChecker();
    wb.setMaxChanges(0);
    assertEquals("maxChanges=0", List.of(), breakSunflower(wb));
  }

  /** The setter accepts any int; a negative maximum allows no word break either. */
  public void testBreaksHonourNegative() throws IOException {
    WordBreakSpellChecker wb = new WordBreakSpellChecker();
    wb.setMaxChanges(-1);
    assertEquals("maxChanges=-1", List.of(), breakSunflower(wb));
  }
}
