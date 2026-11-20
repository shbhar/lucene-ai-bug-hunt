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
package org.apache.lucene.analysis.icu.segmentation;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestICUTokenizerSameTextTwice extends LuceneTestCase {

  public void testSecondAnalysisGivesTheTokensOfTheFirst() throws Exception {
    String text = "コン ーング゠";
    // expected: the same tokens twice; actual in a new JVM: [コン, ーング゠] then [コン, ーン, グ゠]
    assertEquals(tokens(text), tokens(text));
  }

  private static List<String> tokens(String text) throws Exception {
    List<String> terms = new ArrayList<>();
    try (ICUTokenizer tokenizer = new ICUTokenizer()) {
      CharTermAttribute term = tokenizer.addAttribute(CharTermAttribute.class);
      tokenizer.setReader(new StringReader(text));
      tokenizer.reset();
      while (tokenizer.incrementToken()) {
        terms.add(term.toString());
      }
      tokenizer.end();
    }
    return terms;
  }
}
