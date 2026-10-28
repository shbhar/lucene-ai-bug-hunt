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
package org.apache.lucene.analysis.te;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.BytesRef;

/** The two spellings of the letter AU, ఔ (U+0C14) and ఒ + ౌ (U+0C12 U+0C4C), give one term. */
public class TestTeluguAuSpellings extends BaseTokenStreamTestCase {

  /** ఒ + ౌ is composed to AU, which is folded to OO like a precomposed AU. */
  public void testComposedAu() throws IOException {
    check("ఔషధం", "ఓషధం"); // control: precomposed AU, already right
    check("ఒౌషధం", "ఓషధం");
  }

  /** The chain of the factory javadoc: standard tokenizer + teluguNormalization. */
  public void testFactoryExampleChain() throws IOException {
    Analyzer a =
        CustomAnalyzer.builder()
            .withTokenizer("standard")
            .addTokenFilter(TeluguNormalizationFilterFactory.NAME)
            .build();
    assertAnalyzesTo(a, "ఔషధం ఒౌషధం", new String[] {"ఓషధం", "ఓషధం"});
    assertEquals(new BytesRef("ఓషధం"), a.normalize("f", "ఒౌషధం"));
    a.close();
  }

  private void check(String input, String output) throws IOException {
    Tokenizer tokenizer = whitespaceMockTokenizer(input);
    TokenFilter tf = new TeluguNormalizationFilter(tokenizer);
    assertTokenStreamContents(tf, new String[] {output});
  }
}
