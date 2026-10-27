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
package org.apache.lucene.analysis.classic;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.function.Supplier;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.charfilter.HTMLStripCharFilter;
import org.apache.lucene.analysis.email.UAX29URLEmailTokenizer;
import org.apache.lucene.analysis.wikipedia.WikipediaTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * The other JFlex-generated scanners share the refill code: a value that ends with an unpaired high
 * surrogate keeps its tokens, also when it is one char shorter than the scanner's buffer.
 */
public class TestTrailingHighSurrogateJFlexScanners extends BaseTokenStreamTestCase {

  /** The first half of U+1F600 (GRINNING FACE), which is "😀" in UTF-16. */
  private static final String HALF_EMOJI = "\uD83D";

  private static Analyzer analyzer(Supplier<Tokenizer> tokenizer) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(tokenizer.get());
      }
    };
  }

  /** "abcd abcd ... " padded with spaces to length - 1 chars, then HALF_EMOJI: length chars. */
  private static String value(int length) {
    int words = (length - 1) / 5;
    return "abcd ".repeat(words) + " ".repeat((length - 1) % 5) + HALF_EMOJI;
  }

  private static void assertKeepsWords(Supplier<Tokenizer> tokenizer, int bufferSize)
      throws IOException {
    try (Analyzer a = analyzer(tokenizer)) {
      assertAnalyzesTo(a, "hello world" + HALF_EMOJI, new String[] {"hello", "world"});
      String text = value(bufferSize - 1);
      String[] expected = new String[(bufferSize - 2) / 5];
      Arrays.fill(expected, "abcd");
      assertAnalyzesTo(a, text, expected);
    }
  }

  public void testClassicTokenizer() throws IOException {
    assertKeepsWords(ClassicTokenizer::new, 4096);
  }

  public void testUAX29URLEmailTokenizer() throws IOException {
    assertKeepsWords(UAX29URLEmailTokenizer::new, 255);
  }

  public void testWikipediaTokenizer() throws IOException {
    assertKeepsWords(WikipediaTokenizer::new, 4096);
  }

  /** Text without markup passes through the char filter unchanged. */
  public void testHTMLStripCharFilter() throws IOException {
    for (String text : new String[] {"hello world" + HALF_EMOJI, value(16384 - 1)}) {
      StringBuilder out = new StringBuilder();
      try (Reader r = new HTMLStripCharFilter(new StringReader(text))) {
        for (int c = r.read(); c != -1; c = r.read()) {
          out.append((char) c);
        }
      }
      assertEquals("chars out of " + text.length(), text.length(), out.length());
      assertEquals(text, out.toString());
    }
  }
}
