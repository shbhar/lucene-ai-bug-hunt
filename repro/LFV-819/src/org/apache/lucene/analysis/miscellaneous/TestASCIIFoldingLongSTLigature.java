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
package org.apache.lucene.analysis.miscellaneous;

import java.io.IOException;
import java.text.Normalizer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * U+FB05 LATIN SMALL LIGATURE LONG S T (NFKD: "st") is in the Alphabetic Presentation Forms block,
 * which the ASCIIFoldingFilter javadoc lists, and its ASCII form exists; the filter folds its sibling
 * U+FB06 LATIN SMALL LIGATURE ST to "st" and U+017F LATIN SMALL LETTER LONG S to "s", but leaves
 * U+FB05 unchanged.
 */
public class TestASCIIFoldingLongSTLigature extends BaseTokenStreamTestCase {

  private static Analyzer lowercaseThenFold() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new StandardTokenizer();
        TokenStream ts = new LowerCaseFilter(t);
        return new TokenStreamComponents(t, new ASCIIFoldingFilter(ts));
      }
    };
  }

  public void testUnicodeFacts() {
    assertEquals("st", Normalizer.normalize("ﬅ", Normalizer.Form.NFKD));
    assertEquals("st", Normalizer.normalize("ﬆ", Normalizer.Form.NFKD));
  }

  public void testLongSTLigatureFolds() throws IOException {
    try (Analyzer a = lowercaseThenFold()) {
      assertAnalyzesTo(a, "faﬆ", new String[] {"fast"}); // control: ligature st
      assertAnalyzesTo(a, "faſt", new String[] {"fast"}); // control: long s + t
      assertAnalyzesTo(a, "faﬅ", new String[] {"fast"}); // ligature long s t
    }
  }
}
