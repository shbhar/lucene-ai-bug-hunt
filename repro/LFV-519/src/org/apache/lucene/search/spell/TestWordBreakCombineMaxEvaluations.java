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
 * WordBreakSpellChecker.suggestWordCombinations stops at maxEvaluations ("The maximum number of
 * word combinations to evaluate") only for the one left term at which the counter hits the limit
 * exactly (== and an inner-loop break); every later left term is still evaluated.
 */
public class TestWordBreakCombineMaxEvaluations extends LuceneTestCase {

  private static String render(CombineSuggestion[] cs) {
    return Arrays.toString(
        Arrays.stream(cs)
            .map(c -> c.suggestion().string + Arrays.toString(c.originalTermIndexes()))
            .toArray());
  }

  public void testMaxEvaluationsBoundsCombinations() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(null))) {
        Document d = new Document();
        for (String s : new String[] {"ab", "bc", "cd", "de"}) {
          d.add(new StringField("f", s, Field.Store.NO));
        }
        w.addDocument(d);
      }
      try (IndexReader ir = DirectoryReader.open(dir)) {
        Term[] terms = {
          new Term("f", "a"), new Term("f", "b"), new Term("f", "c"), new Term("f", "d"),
          new Term("f", "e")
        };
        WordBreakSpellChecker wbsp = new WordBreakSpellChecker();
        wbsp.setMaxChanges(1);
        // control: without a binding limit all four adjacent pairs combine into indexed words
        assertEquals(4, wbsp.suggestWordCombinations(terms, 10, ir, SuggestMode.SUGGEST_ALWAYS).length);

        for (int maxEvaluations = 1; maxEvaluations <= 3; maxEvaluations++) {
          wbsp.setMaxEvaluations(maxEvaluations);
          CombineSuggestion[] cs =
              wbsp.suggestWordCombinations(terms, 10, ir, SuggestMode.SUGGEST_ALWAYS);
          // each evaluated combination yields at most one suggestion, so if maxEvaluations is
          // respected there cannot be more suggestions than that (as in testMaxEvaluations)
          assertTrue(
              "maxEvaluations=" + maxEvaluations + " but got " + render(cs),
              cs.length <= maxEvaluations);
        }
      }
    }
  }
}
