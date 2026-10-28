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

import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SpellChecker.suggestSimilar raises its accuracy cutoff to the score of the queue's weakest
 * entry once the queue holds numSug words. That is only valid when the comparator orders by score
 * first; with SuggestWordFrequencyComparator ("Frequency first, then score") it rejects a more
 * frequent word whose score is below that entry's score although it would rank first.
 */
public class TestSpellCheckerFrequencyComparatorCutoff extends LuceneTestCase {

  private static final String[] RARE = {
    "relieve", "retrieve", "reprieve", "revive", "recite", "receiver", "deceive", "review", "recede"
  };

  public void testMostFrequentWordRanksFirst() throws Exception {
    try (Directory userDir = newDirectory();
        Directory spellDir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(userDir, new IndexWriterConfig(null))) {
        for (int i = 0; i < 50; i++) {
          Document d = new Document();
          d.add(new StringField("body", "receive", Field.Store.NO));
          w.addDocument(d);
        }
        for (String s : RARE) {
          Document d = new Document();
          d.add(new StringField("body", s, Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (IndexReader ir = DirectoryReader.open(userDir);
          SpellChecker sc =
              new SpellChecker(
                  spellDir, new LevenshteinDistance(), new SuggestWordFrequencyComparator())) {
        sc.indexDictionary(new LuceneDictionary(ir, "body"), new IndexWriterConfig(null), false);
        // 10 words in the spell index <= 10 * numSug n-gram hits, so every word is a hit and the
        // n-gram recall caveat of the javadoc cannot apply.
        assertEquals(50, ir.docFreq(new Term("body", "receive")));
        float receive = sc.getStringDistance().getDistance("recieve", "receive");
        assertTrue(receive >= sc.getAccuracy());

        // control: asking for 4 suggestions puts the most frequent word first
        String[] four =
            sc.suggestSimilar("recieve", 4, ir, "body", SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX);
        assertEquals(Arrays.toString(four), "receive", four[0]);

        for (int numSug = 1; numSug <= 3; numSug++) {
          String[] similar =
              sc.suggestSimilar(
                  "recieve", numSug, ir, "body", SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX);
          assertEquals(
              "numSug=" + numSug + " got " + Arrays.toString(similar), "receive", similar[0]);
        }
      }
    }
  }

  /**
   * The javadoc recommends numSug >= 5; the same happens with numSug = 5 and 10: six rare words one
   * edit away fill the queue first, and the 50-times more frequent word two edits away (still above
   * the default accuracy) is cut off.
   */
  public void testNumSugAtLeastFive() throws Exception {
    String[] rare = {"abcdefx", "abcdefy", "abcdefz", "abcdexg", "abcdeyg", "abcdezg"};
    try (Directory userDir = newDirectory();
        Directory spellDir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(userDir, new IndexWriterConfig(null))) {
        for (int i = 0; i < 50; i++) {
          Document d = new Document();
          d.add(new StringField("body", "abcdfeg", Field.Store.NO));
          w.addDocument(d);
        }
        for (String s : rare) {
          Document d = new Document();
          d.add(new StringField("body", s, Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (IndexReader ir = DirectoryReader.open(userDir);
          SpellChecker sc =
              new SpellChecker(
                  spellDir, new LevenshteinDistance(), new SuggestWordFrequencyComparator())) {
        sc.indexDictionary(new LuceneDictionary(ir, "body"), new IndexWriterConfig(null), false);
        // 7 words in the spell index <= 10 * numSug n-gram hits: every word is a hit
        assertTrue(
            sc.getStringDistance().getDistance("abcdefg", "abcdfeg") >= sc.getAccuracy());
        for (int numSug : new int[] {5, 10}) {
          String[] similar =
              sc.suggestSimilar(
                  "abcdefg", numSug, ir, "body", SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX);
          assertEquals(
              "numSug=" + numSug + " got " + Arrays.toString(similar), "abcdfeg", similar[0]);
        }
      }
    }
  }
}
