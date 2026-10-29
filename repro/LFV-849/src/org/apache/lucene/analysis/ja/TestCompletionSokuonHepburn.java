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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCompletionSokuonHepburn extends LuceneTestCase {

  public void testZasshiIsIndexed() throws Exception {
    List<String> terms = new ArrayList<>();
    try (Analyzer analyzer =
            new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.INDEX);
        TokenStream ts = analyzer.tokenStream("f", "雑誌")) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        terms.add(term.toString());
      }
      ts.end();
    }
    // expected: the Hepburn spelling "zasshi" among the index-side tokens; actual: [雑誌, zassi]
    assertTrue(
        "expected zasshi among the index-side tokens, actual: " + terms, terms.contains("zasshi"));
  }
}
