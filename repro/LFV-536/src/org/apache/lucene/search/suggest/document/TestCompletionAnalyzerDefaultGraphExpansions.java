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
package org.apache.lucene.search.suggest.document;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.CharsRef;

/**
 * CompletionAnalyzer(Analyzer) is documented as "preserving token separation, position increments and no limit on
 * graph expansions", but passes ConcatenateGraphFilter.DEFAULT_MAX_GRAPH_EXPANSIONS (10000), so a value whose
 * analysis has more than 10000 paths is silently indexed with only 10000 of them.
 */
public class TestCompletionAnalyzerDefaultGraphExpansions extends LuceneTestCase {

  public void testDefaultConstructorHasNoGraphExpansionLimit() throws Exception {
    // 14 positions, each with a single-token synonym that starts with a different letter: 2^14 = 16384 paths
    SynonymMap.Builder b = new SynonymMap.Builder(true);
    StringBuilder value = new StringBuilder();
    for (int i = 0; i < 14; i++) {
      b.add(new CharsRef("a" + i), new CharsRef("b" + i), true);
      value.append("a").append(i).append(' ');
    }
    final SynonymMap map = b.build();
    Analyzer base =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String field) {
            Tokenizer t = new WhitespaceTokenizer();
            return new TokenStreamComponents(t, new SynonymGraphFilter(t, map, true));
          }
        };
    CompletionAnalyzer analyzer = new CompletionAnalyzer(base); // "no limit on graph expansions"
    int paths = 0;
    try (TokenStream ts = analyzer.tokenStream("suggest", value.toString().trim())) {
      TermToBytesRefAttribute att = ts.addAttribute(TermToBytesRefAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        paths++;
      }
      ts.end();
    }
    assertEquals(16384, paths);
  }
}
