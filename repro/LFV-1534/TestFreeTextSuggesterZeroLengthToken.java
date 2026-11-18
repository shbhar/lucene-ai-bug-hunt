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
import org.apache.lucene.analysis.ar.ArabicAnalyzer;
import org.apache.lucene.search.spell.PlainTextDictionary;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFreeTextSuggesterZeroLengthToken extends LuceneTestCase {

  // expected: no suggestion ends in a separator; actual: AssertionError (-ea) or "foo" + separator
  public void testNoEmptyWordIsPredicted() throws Exception {
    try (Analyzer analyzer = new ArabicAnalyzer()) {
      FreeTextSuggester suggester = new FreeTextSuggester(analyzer);
      suggester.build(new PlainTextDictionary(new StringReader("foo \u0640 bar")));
      for (LookupResult result : suggester.lookup("foo ", 10)) {
        CharSequence key = result.key;
        assertTrue(
            "a suggestion for \"foo \" predicts the empty word",
            key.charAt(key.length() - 1) != FreeTextSuggester.DEFAULT_SEPARATOR);
      }
    }
  }
}
