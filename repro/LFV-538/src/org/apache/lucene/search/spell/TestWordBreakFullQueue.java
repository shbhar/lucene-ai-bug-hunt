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
import java.util.stream.Collectors;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.spell.WordBreakSpellChecker.BreakSuggestionSortMethod;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestWordBreakFullQueue extends LuceneTestCase {

  public void testFewestBreaksSplitFirst() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (String word : new String[] {"a", "ab", "b", "c", "d", "e"}) {
          doc.add(new StringField("f", word, Field.Store.NO));
        }
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        WordBreakSpellChecker checker = new WordBreakSpellChecker();
        checker.setMaxChanges(4);
        SuggestWord[][] splits =
            checker.suggestWordBreaks(
                new Term("f", "abcde"),
                1,
                reader,
                SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX,
                BreakSuggestionSortMethod.NUM_CHANGES_THEN_MAX_FREQUENCY);
        String top = Arrays.stream(splits[0]).map(s -> s.string).collect(Collectors.joining(" "));
        // expected the 3-break "ab c d e" (fewest breaks first), actual the 4-break "a b c d e"
        assertEquals("ab c d e", top);
      }
    }
  }
}
