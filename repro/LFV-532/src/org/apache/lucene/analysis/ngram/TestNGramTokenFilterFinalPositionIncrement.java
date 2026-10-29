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
package org.apache.lucene.analysis.ngram;

import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestNGramTokenFilterFinalPositionIncrement extends BaseTokenStreamTestCase {

  public void testEndKeepsInputFinalPositionIncrement() throws Exception {
    TokenStream ts =
        new NGramTokenFilter(
            new StopFilter(whitespaceMockTokenizer("abc the"), StopFilter.makeStopSet("the")),
            3, 3, false);
    PositionIncrementAttribute posInc = ts.addAttribute(PositionIncrementAttribute.class);
    ts.reset();
    while (ts.incrementToken()) {}
    ts.end();
    // expected 1 (StopFilter's hole for the removed trailing "the"; the n-gram filter drops nothing), actual 0
    assertEquals("final position increment after end()", 1, posInc.getPositionIncrement());
    ts.close();
  }
}
