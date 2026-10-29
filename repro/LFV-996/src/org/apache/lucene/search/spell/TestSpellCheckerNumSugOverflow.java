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
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSpellCheckerNumSugOverflow extends LuceneTestCase {

  public void testNumSugIntegerMaxValue() throws Exception {
    try (Directory dir = newDirectory();
        SpellChecker sc = new SpellChecker(dir)) {
      sc.indexDictionary(
          new PlainTextDictionary(new StringReader("lucene\n")), new IndexWriterConfig(), false);
      try {
        assertArrayEquals(new String[] {"lucene"}, sc.suggestSimilar("lucenx", Integer.MAX_VALUE));
      } catch (IllegalArgumentException e) {
        fail("expected [lucene] for numSug = Integer.MAX_VALUE; actual: " + e.getMessage());
      }
    }
  }
}
