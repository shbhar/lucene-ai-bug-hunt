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
package org.apache.lucene.analysis.icu.segmentation;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestICUTokenizerRefillSurrogatePair extends LuceneTestCase {

  public void testSupplementaryCharAtChunkEnd() throws Exception {
    String extB = new String(Character.toChars(0x20000));
    String text = "中文".repeat(2048).substring(0, 4095) + extB + "中文";
    List<String> terms = new ArrayList<>();
    try (Tokenizer tokenizer = new ICUTokenizer()) {
      tokenizer.setReader(new StringReader(text));
      CharTermAttribute term = tokenizer.addAttribute(CharTermAttribute.class);
      tokenizer.reset();
      while (tokenizer.incrementToken()) {
        terms.add(term.toString());
      }
      tokenizer.end();
    }
    // high surrogate of U+20000 is the last char of the 4096-char chunk: expected 1, actual 0
    assertEquals("tokens equal to U+20000", 1, Collections.frequency(terms, extB));
  }
}
