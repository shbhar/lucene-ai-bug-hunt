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

public class TestWordBreakCombineLeadingSeparator extends LuceneTestCase {

  public void testLeadingSeparatorTerm() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new StringField("f", "sunflower", Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader ir = DirectoryReader.open(dir)) {
        WordBreakSpellChecker wbsp = new WordBreakSpellChecker();
        Term[] terms = {
          WordBreakSpellChecker.SEPARATOR_TERM, new Term("f", "sun"), new Term("f", "flower")
        };
        CombineSuggestion[] suggestions =
            wbsp.suggestWordCombinations(terms, 5, ir, SuggestMode.SUGGEST_ALWAYS);
        // expected: "sunflower", as without the leading separator; actual: no suggestion (looked up in field "")
        assertEquals(1, suggestions.length);
        assertEquals("sunflower", suggestions[0].suggestion().string);
      }
    }
  }
}
