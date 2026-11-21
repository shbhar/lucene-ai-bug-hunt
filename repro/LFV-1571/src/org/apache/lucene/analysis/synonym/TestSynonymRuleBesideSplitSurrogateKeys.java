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
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.TruncateTokenFilter;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestSynonymRuleBesideSplitSurrogateKeys extends BaseTokenStreamTestCase {

  public void testRuleOfUncutWordFires() throws Exception {
    Analyzer cut =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new WhitespaceTokenizer();
            return new TokenStreamComponents(
                tokenizer, TruncateTokenFilter.truncateAfterChars(tokenizer, 1));
          }
        };
    SolrSynonymParser parser = new SolrSynonymParser(true, true, cut);
    parser.parse(
        new StringReader(
            "a => x\n\uFF21 => y\n\uD83C\uDF00 => p\n\uD83D\uDE00 => q\n\uD83E\uDD14 => r\n"));
    SynonymMap synonyms = parser.build();
    // expected: U+FF21, one UTF-16 unit and so never cut, becomes "y"; actual on 10.6.0: unchanged
    assertTokenStreamContents(
        new SynonymGraphFilter(cut.tokenStream("f", "\uFF21"), synonyms, false),
        new String[] {"y"});
    cut.close();
  }
}
