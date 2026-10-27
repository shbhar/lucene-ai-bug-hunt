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
package org.apache.lucene.analysis.email;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * The same UAX #29 Hebrew quote cases as for StandardTokenizer: UAX29URLEmailTokenizer has the same
 * word rule. Letters: U+05D0 ALEF, U+05D1 BET, U+05D2 GIMEL, U+05D4 HE, U+05DC LAMED, U+05E4 PE,
 * U+05E6 TSADI, U+05E9 SHIN, U+05EA TAV.
 */
public class TestUAX29URLEmailTokenizerHebrewQuotes extends BaseTokenStreamTestCase {

  private Analyzer a;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new UAX29URLEmailTokenizer(newAttributeFactory());
            return new TokenStreamComponents(t);
          }
        };
  }

  @Override
  public void tearDown() throws Exception {
    a.close();
    super.tearDown();
  }

  /** Controls: these pass on affected versions too. */
  public void testControls() throws Exception {
    assertAnalyzesTo(a, "א'", new String[] {"א'"});
    assertAnalyzesTo(a, "תש\"פ", new String[] {"תש\"פ"});
    assertAnalyzesTo(a, "abc'3", new String[] {"abc", "3"});
  }

  public void testApostropheThenDigit() throws Exception {
    assertAnalyzesTo(a, "ב'2", new String[] {"ב'", "2"}, new String[] {"<ALPHANUM>", "<NUM>"});
  }

  public void testApostropheAfterMidWordFullStop() throws Exception {
    assertAnalyzesTo(a, "ה.ש'", new String[] {"ה.ש'"});
  }

  public void testQuotationMarkAfterMidWordCharacter() throws Exception {
    assertAnalyzesTo(a, "א.צ\"ל", new String[] {"א.צ\"ל"});
    assertAnalyzesTo(a, "א\"ב\"ג", new String[] {"א\"ב\"ג"});
  }

  public void testFullStopAfterAcronym() throws Exception {
    assertAnalyzesTo(a, "ת\"א.ב", new String[] {"ת\"א.ב"});
  }
}
