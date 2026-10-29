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
package org.apache.lucene.analysis.th;

import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestThaiAnalyzerNumberAfterPeriod extends BaseTokenStreamTestCase {

  public void testBuddhistEraYear() throws Exception {
    assumeTrue(
        "JRE does not support Thai dictionary-based BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
    try (ThaiAnalyzer a = new ThaiAnalyzer()) {
      // expected: [พ, ศ, 2567]; actual: [พ, ศ] (the year 2567 is dropped)
      assertAnalyzesTo(a, "พ.ศ.2567", new String[] {"พ", "ศ", "2567"});
    }
  }
}
