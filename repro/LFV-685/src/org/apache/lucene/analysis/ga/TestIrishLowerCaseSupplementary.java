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
package org.apache.lucene.analysis.ga;

import java.util.Locale;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIrishLowerCaseSupplementary extends LuceneTestCase {

  public void testSupplementaryUpperCaseIsLowerCased() throws Exception {
    try (Analyzer a = new IrishAnalyzer()) {
      // expected: Deseret capital U+10400 lower-cased to U+10428; actual: U+10400 unchanged
      int cp = a.normalize("f", "\uD801\uDC00").utf8ToString().codePointAt(0);
      assertEquals("U+10428", String.format(Locale.ROOT, "U+%X", cp));
    }
  }
}
