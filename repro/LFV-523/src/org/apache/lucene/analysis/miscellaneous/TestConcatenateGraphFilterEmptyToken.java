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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ar.ArabicAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestConcatenateGraphFilterEmptyToken extends LuceneTestCase {

  public void testPathThroughEmptyTokenIsKept() throws Exception {
    // ArabicAnalyzer turns the standalone tatweel (U+0640) into an empty token: foo, "", bar
    try (Analyzer analyzer = new ArabicAnalyzer();
        TokenStream ts = new ConcatenateGraphFilter(analyzer.tokenStream("f", "foo ـ bar"))) {
      ts.reset();
      int tokens = 0;
      while (ts.incrementToken()) {
        tokens++;
      }
      ts.end();
      assertEquals("output tokens for the one path foo, \"\", bar", 1, tokens);
    }
  }
}
