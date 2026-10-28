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
 * WordBreakSpellChecker#setMaxChanges: "The maximum numbers of changes (word breaks or combinations) to make on
 * the original term(s)". With maxChanges = 0, suggestWordCombinations makes no change, but suggestWordBreaks
 * still returns a one-break suggestion, because generateBreakUpSuggestions starts at numberBreaks = 1 and checks
 * maxChanges only before recursing to a second break.
 */
public class TestWordBreakMaxChangesZero extends LuceneTestCase {

  public void testMaxChangesZero() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(null))) {
        Document d = new Document();
        d.add(new StringField("f", "a", Field.Store.NO));
        d.add(new StringField("f", "b", Field.Store.NO));
        d.add(new StringField("f", "ab", Field.Store.NO));
        w.addDocument(d);
      }
      try (IndexReader ir = DirectoryReader.open(dir)) {
        WordBreakSpellChecker wb = new WordBreakSpellChecker();
        // default maxChanges = 1: one break and one combination are allowed
        assertEquals(1, wb.suggestWordBreaks(new Term("f", "ab"), 5, ir, SuggestMode.SUGGEST_ALWAYS, null).length);
        Term[] terms = {new Term("f", "a"), new Term("f", "b")};
        assertEquals(1, wb.suggestWordCombinations(terms, 5, ir, SuggestMode.SUGGEST_ALWAYS).length);

        wb.setMaxChanges(0);
        assertEquals(0, wb.suggestWordCombinations(terms, 5, ir, SuggestMode.SUGGEST_ALWAYS).length);
        SuggestWord[][] breaks = wb.suggestWordBreaks(new Term("f", "ab"), 5, ir, SuggestMode.SUGGEST_ALWAYS, null);
        assertEquals("maxChanges=0 must allow no word break, got " + breaks.length + " suggestion(s)", 0, breaks.length);
      }
    }
  }
}
