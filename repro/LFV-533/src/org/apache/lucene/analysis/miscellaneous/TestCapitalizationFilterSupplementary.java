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
package org.apache.lucene.analysis.miscellaneous;

import java.io.StringReader;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCapitalizationFilterSupplementary extends LuceneTestCase {

  public void testCapitalizesFirstLetterOutsideBmp() throws Exception {
    Tokenizer tokenizer = new KeywordTokenizer();
    tokenizer.setReader(new StringReader(new String(new int[] {0x1E922, 0x1E923}, 0, 2)));
    try (TokenStream ts = new CapitalizationFilter(tokenizer)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      ts.incrementToken();
      // expected U+1E900 U+1E923 (Adlam capital first letter), actual U+1E922 U+1E923 (unchanged)
      assertEquals(
          List.of("1e900", "1e923"),
          term.toString().codePoints().mapToObj(Integer::toHexString).toList());
    }
  }
}
