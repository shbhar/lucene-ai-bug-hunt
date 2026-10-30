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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ja.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestJapaneseNumberFilterAttributes extends LuceneTestCase {

  public void testComposedNumberKeepsItsOwnAttributes() throws Exception {
    Tokenizer tokenizer = new JapaneseTokenizer(null, false, JapaneseTokenizer.Mode.SEARCH);
    tokenizer.setReader(new StringReader("車は三台"));
    TokenStream ts =
        new JapaneseNumberFilter(new StopFilter(tokenizer, new CharArraySet(List.of("は"), false)));
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    PositionIncrementAttribute posInc = ts.addAttribute(PositionIncrementAttribute.class);
    PartOfSpeechAttribute pos = ts.addAttribute(PartOfSpeechAttribute.class);
    List<String> tokens = new ArrayList<>();
    ts.reset();
    while (ts.incrementToken()) {
      tokens.add(term + " +" + posInc.getPositionIncrement() + " " + pos.getPartOfSpeech());
    }
    ts.end();
    ts.close();
    // expected: 3 keeps the increment and part of speech of 三; actual: it takes those of 台 (+1 名詞-接尾-助数詞)
    assertEquals(List.of("車 +1 名詞-一般", "3 +2 名詞-数", "台 +1 名詞-接尾-助数詞"), tokens);
  }
}
