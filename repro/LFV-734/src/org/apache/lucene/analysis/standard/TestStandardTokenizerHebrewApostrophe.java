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
package org.apache.lucene.analysis.standard;

import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestStandardTokenizerHebrewApostrophe extends BaseTokenStreamTestCase {

  public void testHebrewLetterApostropheThenDigit() throws Exception {
    try (StandardAnalyzer analyzer = new StandardAnalyzer()) {
      // UAX #29: WB7a joins BET and ', no rule joins ' and 2 -> expected [BET', 2]; actual [BET'2]
      assertAnalyzesTo(analyzer, "\u05D1'2", new String[] {"\u05D1'", "2"});
    }
  }
}
