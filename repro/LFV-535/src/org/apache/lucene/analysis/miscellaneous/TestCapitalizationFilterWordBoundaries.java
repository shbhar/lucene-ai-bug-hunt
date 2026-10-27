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

import static org.apache.lucene.analysis.miscellaneous.CapitalizationFilter.DEFAULT_MAX_TOKEN_LENGTH;
import static org.apache.lucene.analysis.miscellaneous.CapitalizationFilter.DEFAULT_MAX_WORD_COUNT;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;

/**
 * CapitalizationFilter splits a token into words at whitespace/control characters and '.', but a
 * word that follows two consecutive delimiters (". ", "  ") or a delimiter at the start of the
 * token is processed with that delimiter as its first character: it is never capitalized, it
 * never matches the keep list or okPrefix, and it is fully lower-cased.
 */
public class TestCapitalizationFilterWordBoundaries extends BaseTokenStreamTestCase {

  private static void assertCapitalizes(String input, String expected, boolean onlyFirstWord, CharArraySet keep)
      throws IOException {
    Tokenizer tok = new MockTokenizer(MockTokenizer.KEYWORD, false);
    tok.setReader(new StringReader(input));
    TokenStream ts =
        new CapitalizationFilter(
            tok, onlyFirstWord, keep, true, null, 0, DEFAULT_MAX_WORD_COUNT, DEFAULT_MAX_TOKEN_LENGTH);
    assertTokenStreamContents(ts, new String[] {expected});
  }

  /** Controls: one delimiter between words works as documented. */
  public void testSingleDelimiter() throws IOException {
    assertCapitalizes("dr jekyll", "Dr Jekyll", false, null);
    assertCapitalizes("st.louis", "St.Louis", false, null);
    assertCapitalizes("made in USA", "Made In USA", false, new CharArraySet(List.of("USA"), false));
    assertCapitalizes("hello world", "Hello world", true, null);
  }

  /** "Each word capitalized": the word after ". " stays lower case. */
  public void testWordAfterPeriodAndSpace() throws IOException {
    assertCapitalizes("dr. jekyll", "Dr. Jekyll", false, null);
  }

  /** The word after two spaces stays lower case. */
  public void testWordAfterTwoSpaces() throws IOException {
    assertCapitalizes("new  york", "New  York", false, null);
  }

  /** Default settings (first word only): a leading space keeps the first word lower case. */
  public void testLeadingSpaceDefaultSettings() throws IOException {
    Tokenizer tok = new MockTokenizer(MockTokenizer.KEYWORD, false);
    tok.setReader(new StringReader(" hello world"));
    assertTokenStreamContents(new CapitalizationFilter(tok), new String[] {" Hello world"});
  }

  /** A keep word after two spaces is not recognised, so it is lower-cased. */
  public void testKeepWordAfterTwoSpaces() throws IOException {
    assertCapitalizes("made in  USA", "Made In  USA", false, new CharArraySet(List.of("USA"), false));
  }
}
