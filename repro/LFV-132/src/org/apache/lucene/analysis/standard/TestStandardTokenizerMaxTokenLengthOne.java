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

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestStandardTokenizerMaxTokenLengthOne extends LuceneTestCase {

  public void testTextAfterSupplementaryCharIsKept() throws IOException {
    String text = "ab \uD801\uDC00 cd";
    StandardTokenizer t = new StandardTokenizer();
    t.setMaxTokenLength(1);
    t.setReader(new StringReader(text));
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    OffsetAttribute offset = t.addAttribute(OffsetAttribute.class);
    List<String> tokens = new ArrayList<>();
    t.reset();
    while (t.incrementToken()) {
      tokens.add(term.toString());
    }
    t.end();
    t.close();
    // expected: c and d are still emitted and the final offset is 8; actual: [a, b] and 3
    assertTrue(
        "expected c and d after U+10400, got " + tokens, tokens.containsAll(List.of("c", "d")));
    assertEquals("final offset", text.length(), offset.endOffset());
  }
}
