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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestThaiSaraAmTwoTones extends LuceneTestCase {

  public void testDecomposedSaraAmWithTwoTones() throws Exception {
    try (Analyzer analyzer = new ThaiAnalyzer()) {
      // expected: Sara Am recomposed (U+0E19 U+0E49 U+0E33); actual: U+0E19 U+0E4D U+0E49 U+0E32
      assertEquals(
          "\u0E19\u0E49\u0E33",
          analyzer.normalize("f", "\u0E19\u0E4D\u0E48\u0E49\u0E32").utf8ToString());
    }
  }
}
