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
package org.apache.lucene.analysis.synonym;

import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.util.CharsRef;

/**
 * SynonymGraphFilter drops the position hole (here a removed stop word) in front of a token that
 * starts a synonym match: the output is placed at the end node of the previous output token. A
 * token that does not match keeps its position increment ("run the fast" without the rule gives
 * run +1, fast +2).
 */
public class TestSynonymGraphFilterHoleBeforeMatch extends BaseTokenStreamTestCase {

  public void testHoleBeforeMatchIsKept() throws Exception {
    SynonymMap.Builder builder = new SynonymMap.Builder(true);
    builder.add(new CharsRef("fast"), new CharsRef("quick"), false);
    SynonymMap map = builder.build();
    try (Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new MockTokenizer(MockTokenizer.WHITESPACE, false);
            TokenStream ts = new StopFilter(t, new CharArraySet(List.of("the"), false));
            return new TokenStreamComponents(t, new SynonymGraphFilter(ts, map, false));
          }
        }) {
      assertAnalyzesTo(a, "run the fast", new String[] {"run", "quick"}, new int[] {1, 2});
    }
  }
}
