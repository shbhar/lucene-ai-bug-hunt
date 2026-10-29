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
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.search.spell.PlainTextDictionary;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFreeTextSuggesterSeparatorInToken extends LuceneTestCase {

  public void testTokenWithDefaultSeparatorRejected() throws Exception {
    try (KeywordAnalyzer analyzer = new KeywordAnalyzer()) {
      FreeTextSuggester suggester = new FreeTextSuggester(analyzer); // bigrams, separator 0x1e
      // javadoc: a token containing the separator byte -> IllegalArgumentException; actual: build succeeds
      expectThrows(
          IllegalArgumentException.class,
          () -> suggester.build(new PlainTextDictionary(new StringReader("foo\u001ebar"))));
    }
  }
}
