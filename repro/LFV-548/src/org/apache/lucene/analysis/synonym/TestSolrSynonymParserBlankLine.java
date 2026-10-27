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

import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * SolrSynonymParser's class javadoc: "Blank lines and lines starting with '#' are comments." A line
 * that contains only spaces or a tab is rejected instead: the whole synonym file fails to parse.
 */
public class TestSolrSynonymParserBlankLine extends BaseTokenStreamTestCase {

  private static SynonymMap parse(String rules) throws Exception {
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    SolrSynonymParser parser = new SolrSynonymParser(true, true, a);
    parser.parse(new StringReader(rules));
    a.close();
    return parser.build();
  }

  private static void assertSynonyms(SynonymMap map, String in, String[] out) throws Exception {
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new MockTokenizer(MockTokenizer.WHITESPACE, false);
            return new TokenStreamComponents(tokenizer, new SynonymGraphFilter(tokenizer, map, false));
          }
        };
    assertAnalyzesTo(analyzer, in, out);
    analyzer.close();
  }

  public void testEmptyLineControl() throws Exception {
    SynonymMap map = parse("couch, sofa\n\nbig, large\n");
    assertSynonyms(map, "couch", new String[] {"sofa", "couch"});
  }

  public void testWhitespaceOnlyLine() throws Exception {
    // the second line is blank: it holds three spaces
    SynonymMap map = parse("couch, sofa\n   \nbig, large\n");
    assertSynonyms(map, "couch", new String[] {"sofa", "couch"});
  }

  public void testTabOnlyLine() throws Exception {
    SynonymMap map = parse("couch, sofa\n\t\nbig, large\n");
    assertSynonyms(map, "big", new String[] {"large", "big"});
  }
}
