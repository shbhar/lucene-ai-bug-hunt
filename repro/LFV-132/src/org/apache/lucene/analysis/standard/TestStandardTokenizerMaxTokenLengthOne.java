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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * setMaxTokenLength accepts [1, MAX_TOKEN_LENGTH_LIMIT] and documents that longer tokens are "chopped up at this
 * token length and emitted as multiple tokens". With maxTokenLength = 1, the first supplementary character (any
 * code point above U+FFFF, e.g. an emoji) ends the token stream: no later text is tokenized and end() reports a
 * final offset short of the input length.
 */
public class TestStandardTokenizerMaxTokenLengthOne extends BaseTokenStreamTestCase {

  // U+10400 DESERET CAPITAL LETTER LONG I (a letter outside the BMP) and U+1F600 GRINNING FACE (emoji)
  private static final String DESERET = "𐐀";
  private static final String EMOJI = "😀";

  private static List<String> terms(Tokenizer t, String text, int[] finalOffset) throws IOException {
    t.setReader(new StringReader(text));
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    OffsetAttribute offset = t.addAttribute(OffsetAttribute.class);
    List<String> out = new ArrayList<>();
    t.reset();
    while (t.incrementToken()) {
      out.add(term.toString());
    }
    t.end();
    finalOffset[0] = offset.endOffset();
    t.close();
    return out;
  }

  /** Control: with maxTokenLength = 2 the words after the supplementary character are tokenized. */
  public void testMaxTokenLengthTwoControl() throws IOException {
    StandardTokenizer t = new StandardTokenizer();
    t.setMaxTokenLength(2);
    String text = "ab " + DESERET + " cd";
    int[] fo = new int[1];
    List<String> got = terms(t, text, fo);
    assertEquals(List.of("ab", DESERET, "cd"), got);
    assertEquals(text.length(), fo[0]);
  }

  /** Text after a supplementary letter must still be tokenized with maxTokenLength = 1. */
  public void testTextAfterSupplementaryLetterIsKept() throws IOException {
    StandardTokenizer t = new StandardTokenizer();
    t.setMaxTokenLength(1);
    String text = "ab " + DESERET + " cd";
    int[] fo = new int[1];
    List<String> got = terms(t, text, fo);
    assertTrue("tokens after U+10400 were dropped: " + got, got.containsAll(List.of("c", "d")));
    assertEquals("final offset must be the input length", text.length(), fo[0]);
  }

  /** The same through StandardAnalyzer, with an emoji. */
  public void testTextAfterEmojiIsKeptByStandardAnalyzer() throws IOException {
    try (StandardAnalyzer a = new StandardAnalyzer()) {
      a.setMaxTokenLength(1);
      String text = "hello " + EMOJI + " world";
      List<String> got = new ArrayList<>();
      try (TokenStream ts = a.tokenStream("f", text)) {
        CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
        ts.reset();
        while (ts.incrementToken()) {
          got.add(term.toString());
        }
        ts.end();
      }
      assertTrue("tokens after U+1F600 were dropped: " + got, got.containsAll(List.of("w", "o", "r", "l", "d")));
    }
  }

  /** A supplementary letter inside a word, through an Analyzer: the rest of the word is dropped. */
  public void testTextAfterSupplementaryLetterInsideWordIsKept() throws IOException {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            StandardTokenizer t = new StandardTokenizer(newAttributeFactory());
            t.setMaxTokenLength(1);
            return new TokenStreamComponents(t);
          }
        };
    // StandardTokenizer with maxTokenLength 255 gives the single token "x" + U+10400 + "y" here; with
    // maxTokenLength 1 the chopped output must at least keep "y" and end at offset 4.
    String text = "x" + DESERET + "y";
    List<String> got = new ArrayList<>();
    int finalOffset;
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      OffsetAttribute off = ts.addAttribute(OffsetAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        got.add(term.toString());
      }
      ts.end();
      finalOffset = off.endOffset();
    }
    a.close();
    assertTrue("token 'y' after U+10400 was dropped: " + got, got.contains("y"));
    assertEquals(4, finalOffset);
  }
}
