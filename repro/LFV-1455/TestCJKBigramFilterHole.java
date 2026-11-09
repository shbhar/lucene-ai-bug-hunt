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
package org.apache.lucene.analysis.cjk;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestCJKBigramFilterHole extends BaseTokenStreamTestCase {

  public void testHoleBeforeCJKToken() throws Exception {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new StandardTokenizer();
            return new TokenStreamComponents(
                t, new CJKBigramFilter(new StopFilter(t, StopFilter.makeStopSet("the"))));
          }
        };
    // expected: the removed "the" leaves a hole (東京 +2, as "bar" gets in "foo the bar");
    // actual: 東京 +1
    assertAnalyzesTo(a, "foo the 東京", new String[] {"foo", "東京"}, new int[] {1, 2});
    a.close();
  }
}
