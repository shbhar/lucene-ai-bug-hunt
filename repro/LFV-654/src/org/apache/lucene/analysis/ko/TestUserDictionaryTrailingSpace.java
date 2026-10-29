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
package org.apache.lucene.analysis.ko;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.dict.UserDictionary;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestUserDictionaryTrailingSpace extends LuceneTestCase {

  private static List<String> terms(String userDictLine, String text) throws Exception {
    List<String> terms = new ArrayList<>();
    try (Analyzer analyzer =
            new KoreanAnalyzer(
                UserDictionary.open(new StringReader(userDictLine)),
                KoreanTokenizer.DEFAULT_DECOMPOUND,
                KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS,
                false);
        TokenStream ts = analyzer.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        terms.add(term.toString());
      }
      ts.end();
    }
    return terms;
  }

  public void testTrailingSpaceInUserDictionaryLine() throws Exception {
    // expected line "사람 " to give the terms of line "사람" ([사람]); actual [사람, 이]
    assertEquals(terms("사람", "사람이나"), terms("사람 ", "사람이나"));
  }
}
