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
package org.apache.lucene.analysis.phonetic;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestDoubleMetaphoneFilterTokenEqualsCode extends BaseTokenStreamTestCase {

  public void testTokenEqualToPrimaryCodeGetsAlternateCode() throws Exception {
    List<String> terms = new ArrayList<>();
    try (TokenStream ts = new DoubleMetaphoneFilter(whitespaceMockTokenizer("JR"), 4, true)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        terms.add(term.toString());
      }
      ts.end();
    }
    // "JR" (like "jr") has primary code JR and alternate code AR: expected [JR, AR]; actual [JR]
    assertEquals(List.of("JR", "AR"), terms);
  }
}
