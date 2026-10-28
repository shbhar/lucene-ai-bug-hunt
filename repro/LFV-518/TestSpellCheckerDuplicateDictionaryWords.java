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

import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SpellChecker.indexDictionary skips words that are already in the spell index, but only checks
 * the index as it was before the call: a word that occurs twice in one dictionary is indexed twice,
 * and suggestSimilar then returns it twice, pushing a different word out of the results.
 */
public class TestSpellCheckerDuplicateDictionaryWords extends LuceneTestCase {

  public void testWordRepeatedInOneDictionary() throws Exception {
    try (Directory spellDir = newDirectory();
        SpellChecker sc = new SpellChecker(spellDir)) {
      sc.indexDictionary(
          new PlainTextDictionary(new StringReader("relieve\nrelieve\nreceive\n")),
          new IndexWriterConfig(null),
          false);
      String[] similar = sc.suggestSimilar("recieve", 2);
      assertEquals(
          "duplicate suggestions: " + Arrays.toString(similar),
          similar.length,
          new HashSet<>(Arrays.asList(similar)).size());
      assertArrayEquals(new String[] {"relieve", "receive"}, similar);
    }
  }

  /** control: the same words split over two calls are de-duplicated as intended */
  public void testWordRepeatedAcrossCallsIsNotDuplicated() throws Exception {
    try (Directory spellDir = newDirectory();
        SpellChecker sc = new SpellChecker(spellDir)) {
      sc.indexDictionary(
          new PlainTextDictionary(new StringReader("relieve\n")), new IndexWriterConfig(null), false);
      sc.indexDictionary(
          new PlainTextDictionary(new StringReader("relieve\nreceive\n")),
          new IndexWriterConfig(null),
          false);
      assertArrayEquals(new String[] {"relieve", "receive"}, sc.suggestSimilar("recieve", 2));
    }
  }
}
