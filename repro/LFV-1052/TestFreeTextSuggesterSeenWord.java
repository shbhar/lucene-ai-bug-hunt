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
package org.apache.lucene.search.suggest.analyzing;

import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.spell.PlainTextDictionary;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFreeTextSuggesterSeenWord extends LuceneTestCase {

  public void testAlreadyPredictedWordDoesNotTakeUnigramSlot() throws Exception {
    try (Analyzer a = new MockAnalyzer(random())) {
      FreeTextSuggester sug = new FreeTextSuggester(a);
      sug.build(
          new PlainTextDictionary(
              new StringReader("foo ".repeat(9) + "bar ".repeat(8) + "barn ".repeat(7))));
      // expected barn (0.4*7/24 = 0.117 beats "foo bar" 1/9 = 0.111), actual "foo bar"
      String top = sug.lookup("foo bar", 1).get(0).key.toString().replace('\u001e', ' ');
      assertEquals("barn", top);
    }
  }
}
