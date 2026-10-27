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

/** A medial visarga geminates the next letter; the copy must be normalized like the letter. */
public class TestBengaliVisarga extends BaseTokenStreamTestCase {

  /** SHA after a medial visarga: both copies become SA, as Ba-phalaa gives বিশ্ব -> বিসস. */
  public void testVisargaBeforeSha() throws IOException {
    check("নিঃশব্দ", "নিসসব্দ");
    check("নিঃশেষ", "নিসসেস");
  }

  /** The SSA and SA spellings of the same word give the same output. */
  public void testVisargaBeforeSsaAndSa() throws IOException {
    check("নিঃসব্দ", "নিসসব্দ"); // control: already right
    check("নিঃষব্দ", "নিসসব্দ");
  }

  /** Through BengaliAnalyzer, the SHA spelling gets the same term as the SA spelling. */
  public void testAnalyzerVisargaSpellingsAgree() throws IOException {
    Analyzer a = new BengaliAnalyzer();
    checkOneTerm(a, "নিঃসব্দ", "নিসসব্দ"); // control: already right
    checkOneTerm(a, "নিঃশব্দ", "নিসসব্দ");
    checkOneTerm(a, "নিঃশ্বাস", "নিসসসাস");
    a.close();
  }

  private void check(String input, String output) throws IOException {
    Tokenizer tokenizer = whitespaceMockTokenizer(input);
    TokenFilter tf = new BengaliNormalizationFilter(tokenizer);
    assertTokenStreamContents(tf, new String[] {output});
  }
}
