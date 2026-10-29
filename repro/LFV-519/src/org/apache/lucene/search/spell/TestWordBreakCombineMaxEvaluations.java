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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestWordBreakCombineMaxEvaluations extends LuceneTestCase {

  public void testMaxEvaluationsLimitsCombinations() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (String word : new String[] {"ab", "bc", "bcd"}) {
          doc.add(new StringField("f", word, Field.Store.NO));
        }
        writer.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        Term[] terms = {
          new Term("f", "a"), new Term("f", "b"), new Term("f", "c"), new Term("f", "d")
        };
        WordBreakSpellChecker spellChecker = new WordBreakSpellChecker();
        spellChecker.setMaxChanges(2);
        spellChecker.setMaxEvaluations(1);
        CombineSuggestion[] suggestions =
            spellChecker.suggestWordCombinations(terms, 10, reader, SuggestMode.SUGGEST_ALWAYS);
        // expected: 1 combination evaluated, so at most 1 suggestion; actual: ab, bc and bcd
        assertTrue(
            "expected at most 1 suggestion with maxEvaluations=1, got "
                + Arrays.toString(Arrays.stream(suggestions).map(s -> s.suggestion().string).toArray()),
            suggestions.length <= 1);
      }
    }
  }
}
