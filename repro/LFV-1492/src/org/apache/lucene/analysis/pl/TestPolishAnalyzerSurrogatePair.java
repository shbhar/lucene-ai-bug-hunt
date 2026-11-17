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
package org.apache.lucene.analysis.pl;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.UnicodeUtil;

public class TestPolishAnalyzerSurrogatePair extends BaseTokenStreamTestCase {

  public void testTermIsValidUTF16() throws Exception {
    try (Analyzer analyzer = new PolishAnalyzer();
        TokenStream stream = analyzer.tokenStream("field", "\uD835\uDCDFij\u0119")) {
      CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
      stream.reset();
      assertTrue(stream.incrementToken());
      // expected: whole characters; actual: U+D835 (half of U+1D4DF), then 's', a-ogonek, c-acute
      assertTrue("term has an unpaired surrogate", UnicodeUtil.validUTF16String(term));
      assertFalse(stream.incrementToken());
      stream.end();
    }
  }
}
