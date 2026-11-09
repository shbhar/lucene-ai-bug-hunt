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
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ja.dict.UserDictionary;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestUserDictionaryPunctuationSegment extends BaseTokenStreamTestCase {

  public void testDiscardPunctuationAppliesToUserDictionarySegments() throws Exception {
    UserDictionary dict =
        UserDictionary.open(new StringReader("ダ・ヴィンチ,ダ ・ ヴィンチ,ダ ・ ヴィンチ,カスタム名詞"));
    Tokenizer tokenizer =
        new JapaneseTokenizer(newAttributeFactory(), dict, true, JapaneseTokenizer.Mode.SEARCH);
    tokenizer.setReader(new StringReader("ダ・ヴィンチ"));
    // expected [ダ, ヴィンチ] (discardPunctuation=true), actual [ダ, ・, ヴィンチ]
    assertTokenStreamContents(tokenizer, new String[] {"ダ", "ヴィンチ"});
  }
}
