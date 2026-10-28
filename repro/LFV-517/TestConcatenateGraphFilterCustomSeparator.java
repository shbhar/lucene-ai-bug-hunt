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

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;

/** ConcatenateGraphFilter with a custom tokenSeparator joins the tokens with that separator. */
public class TestConcatenateGraphFilterCustomSeparator extends BaseTokenStreamTestCase {

  private static Tokenizer tokenizer(CharacterRunAutomaton pattern, String text) {
    Tokenizer tokenizer = new MockTokenizer(pattern, false);
    tokenizer.setReader(new StringReader(text));
    return tokenizer;
  }

  private static List<BytesRef> terms(TokenStream stream) throws IOException {
    List<BytesRef> terms = new ArrayList<>();
    try (stream) {
      TermToBytesRefAttribute termAtt = stream.addAttribute(TermToBytesRefAttribute.class);
      stream.reset();
      while (stream.incrementToken()) {
        terms.add(BytesRef.deepCopyOf(termAtt.getBytesRef()));
      }
      stream.end();
    }
    return terms;
  }

  /** Control: no token contains the separator. Passes today. */
  public void testAsciiSeparator() throws IOException {
    Tokenizer tokenizer = tokenizer(MockTokenizer.WHITESPACE, "new york");
    ConcatenateGraphFilter stream = new ConcatenateGraphFilter(tokenizer, '-', false, 100);
    assertTokenStreamContents(stream, new String[] {"new-york"}, null, null, new int[] {1});
  }

  /** A single token has nothing to join: it must come out unchanged. Today: "e--mail". */
  public void testSingleTokenContainingSeparator() throws IOException {
    Tokenizer tokenizer = tokenizer(MockTokenizer.KEYWORD, "e-mail");
    ConcatenateGraphFilter stream = new ConcatenateGraphFilter(tokenizer, '-', false, 100);
    assertTokenStreamContents(stream, new String[] {"e-mail"}, null, null, new int[] {1});
  }

  /** "e-mail" and "x" joined with '-' is "e-mail-x". Today: "e--mail-x". */
  public void testTokenContainingSeparator() throws IOException {
    Tokenizer tokenizer = tokenizer(MockTokenizer.WHITESPACE, "e-mail x");
    ConcatenateGraphFilter stream = new ConcatenateGraphFilter(tokenizer, '-', false, 100);
    assertTokenStreamContents(stream, new String[] {"e-mail-x"}, null, null, new int[] {1});
  }

  /**
   * U+2013 EN DASH must be written as its UTF-8 bytes e2 80 93. Today: AssertionError with
   * assertions enabled, the single byte 13 without.
   */
  public void testNonAsciiSeparator() throws IOException {
    Tokenizer tokenizer = tokenizer(MockTokenizer.WHITESPACE, "new york");
    ConcatenateGraphFilter stream = new ConcatenateGraphFilter(tokenizer, '–', false, 100);
    assertEquals(List.of(new BytesRef("new–york")), terms(stream));
  }

  /**
   * U+00B7 MIDDLE DOT must be written as its UTF-8 bytes c2 b7. Today: AssertionError with
   * assertions enabled, the lone byte b7 (invalid UTF-8) without.
   */
  public void testLatin1Separator() throws IOException {
    Tokenizer tokenizer = tokenizer(MockTokenizer.WHITESPACE, "new york");
    ConcatenateGraphFilter stream = new ConcatenateGraphFilter(tokenizer, '·', false, 100);
    assertEquals(List.of(new BytesRef("new·york")), terms(stream));
  }
}
