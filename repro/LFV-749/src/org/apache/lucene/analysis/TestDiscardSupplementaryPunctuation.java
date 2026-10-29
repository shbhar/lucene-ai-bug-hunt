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
package org.apache.lucene.analysis;

import java.io.StringReader;
import org.apache.lucene.analysis.ja.JapaneseTokenizer;
import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestDiscardSupplementaryPunctuation extends BaseTokenStreamTestCase {

  // U+10100 is General_Category Po: expected [X, X], actual [X, U+10100, X]
  private static final String PUNCT = new String(Character.toChars(0x10100));

  public void testJapaneseTokenizer() throws Exception {
    Tokenizer t = new JapaneseTokenizer(null, true, JapaneseTokenizer.Mode.SEARCH);
    t.setReader(new StringReader("日本 " + PUNCT + " 日本"));
    assertTokenStreamContents(t, new String[] {"日本", "日本"});
  }

  public void testKoreanTokenizer() throws Exception {
    Tokenizer t = new KoreanTokenizer();
    t.setReader(new StringReader("한국 " + PUNCT + " 한국"));
    assertTokenStreamContents(t, new String[] {"한국", "한국"});
  }
}
