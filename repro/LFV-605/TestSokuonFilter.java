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
package org.apache.lucene.analysis.ja;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * End to end: JapaneseTokenizer (dictionary reading) + JapaneseReadingFormFilter(useRomaji=true).
 * Public API only; compiles against older releases too (no dict.ToStringUtil import).
 */
public class TestSokuonFilter extends BaseTokenStreamTestCase {

  private Analyzer romaji() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tok =
            new JapaneseTokenizer(newAttributeFactory(), null, true, JapaneseTokenizer.Mode.SEARCH);
        return new TokenStreamComponents(tok, new JapaneseReadingFormFilter(tok, true));
      }
    };
  }

  /** Control: a k-row geminate is doubled by the filter. */
  public void testControlKitte() throws Exception {
    try (Analyzer a = romaji()) {
      assertAnalyzesTo(a, "キップ", new String[] {"kippu"});
    }
  }

  public void testBed() throws Exception {
    try (Analyzer a = romaji()) {
      assertAnalyzesTo(a, "ベッド", new String[] {"beddo"});
    }
  }

  public void testBag() throws Exception {
    try (Analyzer a = romaji()) {
      assertAnalyzesTo(a, "バッグ", new String[] {"baggu"});
    }
  }
}
