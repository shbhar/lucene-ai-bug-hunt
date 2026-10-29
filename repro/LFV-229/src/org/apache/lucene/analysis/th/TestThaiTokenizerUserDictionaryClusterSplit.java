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
package org.apache.lucene.analysis.th;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestThaiTokenizerUserDictionaryClusterSplit extends BaseTokenStreamTestCase {

  public void testDictionaryWordEndingBeforeToneMark() throws IOException {
    assumeTrue("needs the Thai BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
    StringBuilder tokens = new StringBuilder();
    try (Tokenizer t = new ThaiTokenizer(new CharArraySet(List.of("แม่น"), false))) {
      t.setReader(new StringReader("แม่น้ำ"));
      CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
      t.reset();
      while (t.incrementToken()) {
        tokens.append(term);
      }
      t.end();
    }
    // expected: the tokens hold all of แม่น้ำ; on main the tone mark and SARA AM are dropped
    assertEquals("แม่น้ำ", tokens.toString());
  }
}
