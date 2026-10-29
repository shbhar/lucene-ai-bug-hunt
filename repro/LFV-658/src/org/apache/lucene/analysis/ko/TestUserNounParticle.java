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

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.dict.UserDictionary;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestUserNounParticle extends LuceneTestCase {

  public void testUserNounKeepsSubjectParticle() throws IOException {
    UserDictionary userDict = UserDictionary.open(new StringReader("학교"));
    try (Analyzer plain = new KoreanAnalyzer();
        Analyzer withUserNoun =
            new KoreanAnalyzer(
                userDict,
                KoreanTokenizer.DEFAULT_DECOMPOUND,
                KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS,
                false)) {
      // expected: [학교], as without the user entry 학교; actual: [학교, 가]
      assertEquals(terms(plain, "학교가"), terms(withUserNoun, "학교가"));
    }
  }

  private static List<String> terms(Analyzer analyzer, String text) throws IOException {
    List<String> terms = new ArrayList<>();
    try (TokenStream ts = analyzer.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        terms.add(term.toString());
      }
      ts.end();
    }
    return terms;
  }
}
