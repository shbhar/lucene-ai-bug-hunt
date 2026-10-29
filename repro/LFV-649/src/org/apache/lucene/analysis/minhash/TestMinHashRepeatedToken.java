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
package org.apache.lucene.analysis.minhash;

import java.io.IOException;
import java.io.StringReader;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMinHashRepeatedToken extends LuceneTestCase {

  public void testRepeatedTokenKeepsBothLowestHashes() throws IOException {
    // expected: 'b a a' keeps the same 2 lowest hashes as 'b a'; actual: only 1 hash is kept
    assertEquals(minHashes("b a"), minHashes("b a a"));
  }

  /** The output terms of MinHashFilter with 1 hash, 1 bucket and hashSetSize 2. */
  private static Set<String> minHashes(String text) throws IOException {
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(text));
    Set<String> terms = new TreeSet<>();
    try (TokenStream ts = new MinHashFilter(tokenizer, 1, 1, 2, false)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        terms.add(term.toString());
      }
      ts.end();
    }
    return terms;
  }
}
