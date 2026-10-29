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
package org.apache.lucene.analysis.compound;

import java.io.StringReader;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.xml.sax.InputSource;

public class TestHyphenationGrammarLongPatternsLoad extends LuceneTestCase {

  public void testTwoPatternsSharingA2100LetterTailLoad() throws Exception {
    String a = "a".repeat(2100);
    String grammar =
        "<hyphenation-info><patterns>" + a + "1 b" + a + "1c</patterns></hyphenation-info>";
    try {
      HyphenationCompoundWordTokenFilter.getHyphenationTree(
          new InputSource(new StringReader(grammar)));
    } catch (Throwable t) {
      // Expected: the 2-pattern grammar loads; actual: trimToSize() -> compact() throws AIOOBE.
      throw new AssertionError("loading a 2-pattern grammar threw " + t, t);
    }
  }
}
