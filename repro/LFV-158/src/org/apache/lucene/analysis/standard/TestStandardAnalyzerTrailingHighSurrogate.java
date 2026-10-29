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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestStandardAnalyzerTrailingHighSurrogate extends LuceneTestCase {

  public void testTrailingUnpairedHighSurrogate() throws Exception {
    List<String> terms = new ArrayList<>();
    int finalOffset;
    try (Analyzer a = new StandardAnalyzer();
        TokenStream ts = a.tokenStream("f", "hello world\uD83D")) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      OffsetAttribute offset = ts.addAttribute(OffsetAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        terms.add(term.toString());
      }
      ts.end();
      finalOffset = offset.endOffset();
    }
    // expected [hello, world] with final offset 12 (the input length); actual [] with final offset 0
    assertEquals("[hello, world] finalOffset=12", terms + " finalOffset=" + finalOffset);
  }
}
