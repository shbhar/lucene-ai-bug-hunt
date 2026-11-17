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
package org.apache.lucene.analysis.tests;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestCustomAnalyzerDefaultBuilderStopwords extends BaseTokenStreamTestCase {

  // expected: [hund], as on the class path; on the module path: IOException "Resource not found"
  public void testStopwordsShippedWithLucene() throws Exception {
    try (Analyzer a =
        CustomAnalyzer.builder()
            .withTokenizer("whitespace")
            .addTokenFilter(
                "stop",
                "words",
                "org/apache/lucene/analysis/snowball/german_stop.txt",
                "format",
                "snowball")
            .build()) {
      assertAnalyzesTo(a, "der hund", new String[] {"hund"});
    }
  }
}
