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
package org.apache.lucene.analysis.bn;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * The "Khio (Ka + Hoshonto + Murdorno Sh)" rule of BengaliNormalizer: the conjunct KA + HASANTA +
 * SSA (U+0995 U+09CD U+09B7) becomes KHA (U+0996) at the start of a word and KA + KHA elsewhere,
 * the two cases of the paper that the normalizer's javadoc cites.
 */
public class TestBengaliKhio extends BaseTokenStreamTestCase {

  /** Word-initial khio: KA HASANTA SSA MA AA becomes KHA MA AA. */
  public void testWordInitialKhio() throws IOException {
    check("ক্ষমা", "খমা");
  }

  /** Medial khio: KA HASANTA SSA becomes KA KHA (and II becomes I by the DirghoI rule). */
  public void testMedialKhio() throws IOException {
    check("পরীক্ষা", "পরিকখা");
  }

  /** Through BengaliAnalyzer, the khio spelling and the KHA spelling get the same term. */
  public void testAnalyzerKhioMatchesKha() throws IOException {
    Analyzer a = new BengaliAnalyzer();
    checkOneTerm(a, "খমা", "খম"); // control: the KHA spelling
    checkOneTerm(a, "ক্ষমা", "খম");
    a.close();
  }

  private void check(String input, String output) throws IOException {
    Tokenizer tokenizer = whitespaceMockTokenizer(input);
    TokenFilter tf = new BengaliNormalizationFilter(tokenizer);
    assertTokenStreamContents(tf, new String[] {output});
  }
}
