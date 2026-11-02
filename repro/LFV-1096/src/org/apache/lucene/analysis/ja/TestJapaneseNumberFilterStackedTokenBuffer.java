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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * JapaneseNumberFilter, composing 十 and 一, meets the compound 一石二鳥 stacked (position increment 0)
 * on 一: it emits 十 as-is, drops 一 and keeps 十 in its number buffer, so the unrelated number 五
 * later in the text is normalized as 十五 = 15. The tokenizer keeps compounds (discardCompoundToken =
 * false), as in TestJapaneseNumberFilter.
 */
public class TestJapaneseNumberFilterStackedTokenBuffer extends LuceneTestCase {

  public void testLaterNumberIsNotCorrupted() throws IOException {
    try (Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t =
                new JapaneseTokenizer(
                    TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
                    null,
                    false,
                    false,
                    JapaneseTokenizer.Mode.SEARCH);
            return new TokenStreamComponents(t, new JapaneseNumberFilter(t));
          }
        }) {
      List<String> terms = new ArrayList<>();
      try (TokenStream ts = a.tokenStream("f", "十一石二鳥と五")) {
        CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
        ts.reset();
        while (ts.incrementToken()) {
          terms.add(term.toString());
        }
        ts.end();
      }
      assertEquals("tokens: " + terms, "5", terms.get(terms.size() - 1));
    }
  }
}
