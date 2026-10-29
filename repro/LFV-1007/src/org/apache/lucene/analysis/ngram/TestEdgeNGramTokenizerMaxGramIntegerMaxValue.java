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
package org.apache.lucene.analysis.ngram;

import java.io.StringReader;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestEdgeNGramTokenizerMaxGramIntegerMaxValue extends BaseTokenStreamTestCase {

  public void testSeventeenCharText() throws Exception {
    // expected: the 17 prefixes or an IllegalArgumentException; actual: ArrayIndexOutOfBounds
    String text = "abcdefghijklmnopq";
    String[] prefixes = new String[text.length()];
    for (int i = 0; i < prefixes.length; i++) {
      prefixes[i] = text.substring(0, i + 1);
    }
    EdgeNGramTokenizer tokenizer;
    try {
      tokenizer = new EdgeNGramTokenizer(1, Integer.MAX_VALUE);
    } catch (IllegalArgumentException acceptable) {
      return;
    }
    tokenizer.setReader(new StringReader(text));
    try {
      assertTokenStreamContents(tokenizer, prefixes);
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new AssertionError(
          "expected the 17 prefixes of " + text + " or an IllegalArgumentException, got " + e, e);
    }
  }
}
