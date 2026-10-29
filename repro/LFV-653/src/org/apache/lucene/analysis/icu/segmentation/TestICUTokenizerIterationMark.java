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
import java.util.List;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestICUTokenizerIterationMark extends LuceneTestCase {

  public void testIterationMarkKeptWithCjkAsWordsFalse() throws Exception {
    List<String> terms = new ArrayList<>();
    try (Tokenizer t = new ICUTokenizer(new DefaultICUTokenizerConfig(false, true))) {
      t.setReader(new StringReader("人々"));
      CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
      t.reset();
      while (t.incrementToken()) {
        terms.add(term.toString());
      }
      t.end();
    }
    // expected [人, 々] (U+3005 is a UAX #29 letter, kept by StandardTokenizer); actual [人]
    assertEquals(List.of("人", "々"), terms);
  }
}
