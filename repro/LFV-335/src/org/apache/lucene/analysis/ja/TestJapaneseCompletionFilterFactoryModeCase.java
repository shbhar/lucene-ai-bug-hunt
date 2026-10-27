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
package org.apache.lucene.analysis.ja;

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * JapaneseCompletionFilterFactory calls toUpperCase(Locale.ROOT) on the default mode name (already
 * upper case) instead of on the configured value, so mode="query" is rejected, while the sibling
 * JapaneseTokenizerFactory upper-cases the configured value and accepts mode="search". Public API
 * only.
 */
public class TestJapaneseCompletionFilterFactoryModeCase extends LuceneTestCase {

  /** Control: the tokenizer factory of the same module accepts a lower-case mode. */
  public void testTokenizerFactoryAcceptsLowercaseMode() {
    Map<String, String> args = new HashMap<>();
    args.put("mode", "search");
    assertNotNull(new JapaneseTokenizerFactory(args));
  }

  public void testCompletionFactoryAcceptsLowercaseMode() {
    Map<String, String> args = new HashMap<>();
    args.put("mode", "query");
    JapaneseCompletionFilterFactory factory = new JapaneseCompletionFilterFactory(args);
    assertNotNull(factory);
  }

  public void testCustomAnalyzerWithLowercaseMode() throws Exception {
    try (Analyzer a =
        CustomAnalyzer.builder()
            .withTokenizer("japanese", "mode", "normal")
            .addTokenFilter("japaneseCompletion", "mode", "query")
            .build()) {
      assertNotNull(a);
    }
  }
}
