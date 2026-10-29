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
package org.apache.lucene.analysis.shingle;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestShingleFilterHoleBeforeLastWord extends BaseTokenStreamTestCase {

  public void testUnigramAfterRemovedStopWord() throws Exception {
    TokenStream ts =
        new ShingleFilter(
            new StopFilter(whitespaceMockTokenizer("the cat"), StopFilter.makeStopSet("the")),
            3,
            3);
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    List<String> tokens = new ArrayList<>();
    ts.reset();
    while (ts.incrementToken()) {
      tokens.add(term.toString());
    }
    ts.end();
    ts.close();
    // expected [cat] (unigrams are on by default); actual: no token, the stream ends before "cat"
    assertEquals(List.of("cat"), tokens);
  }
}
