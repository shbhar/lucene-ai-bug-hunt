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
package org.apache.lucene.analysis.ko.dict;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.KoreanAnalyzer;
import org.apache.lucene.analysis.ko.KoreanPartOfSpeechStopFilter;
import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.analysis.ko.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A user noun's right context id (does its last syllable end in a consonant?) must depend on the
 * noun only, not on how its line is written: trailing whitespace, or a '#' comment after a space,
 * must not change it.
 */
public class TestUserDictionaryLineFormat extends LuceneTestCase {

  private static UserDictionary dict(String line) throws IOException {
    return UserDictionary.open(new StringReader(line));
  }

  /** The right context id stored for {@code surface}, the only entry of {@code line}. */
  private static int rightId(String line, String surface) throws IOException {
    UserDictionary dict = dict(line);
    char[] chars = surface.toCharArray();
    List<Integer> wordIds = dict.lookup(chars, 0, chars.length);
    assertEquals(1, wordIds.size());
    return dict.getMorphAttributes().getRightId(wordIds.get(0));
  }

  /** term/POS of each token of {@code text} (KoreanTokenizer, DecompoundMode.NONE). */
  private static String tagged(String line, String text) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (KoreanTokenizer tokenizer =
        new KoreanTokenizer(
            TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
            dict(line),
            KoreanTokenizer.DecompoundMode.NONE,
            false,
            true)) {
      CharTermAttribute term = tokenizer.addAttribute(CharTermAttribute.class);
      PartOfSpeechAttribute pos = tokenizer.addAttribute(PartOfSpeechAttribute.class);
      tokenizer.setReader(new StringReader(text));
      tokenizer.reset();
      while (tokenizer.incrementToken()) {
        sb.append(term).append('/').append(pos.getLeftPOS()).append(' ');
      }
      tokenizer.end();
    }
    return sb.toString().trim();
  }

  /** The terms KoreanAnalyzer indexes for {@code text} (default decompound mode and stop tags). */
  private static String terms(String line, String text) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (Analyzer analyzer =
            new KoreanAnalyzer(
                dict(line),
                KoreanTokenizer.DEFAULT_DECOMPOUND,
                KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS,
                false);
        TokenStream ts = analyzer.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        sb.append('[').append(term).append(']');
      }
      ts.end();
    }
    return sb.toString();
  }

  /** Control: a comment with no space before '#' leaves exactly the noun, like the plain line. */
  public void testCommentWithoutSpace() throws IOException {
    assertEquals(rightId("학교", "학교"), rightId("학교# memo", "학교"));
    assertEquals(rightId("사람", "사람"), rightId("사람# memo", "사람"));
  }

  /** Control: a line that starts with whitespace has an empty surface form and is rejected. */
  public void testLeadingWhitespaceIsRejected() {
    expectThrows(IllegalArgumentException.class, () -> dict(" 학교"));
  }

  public void testTrailingWhitespaceKeepsRightId() throws IOException {
    // 사람 ("person") ends in a syllable with a final consonant, 학교 ("school") in one without
    for (String noun : new String[] {"사람", "학교"}) {
      int plain = rightId(noun, noun);
      assertEquals("'" + noun + " '", plain, rightId(noun + " ", noun));
      assertEquals("'" + noun + "<TAB>'", plain, rightId(noun + "\t", noun));
    }
  }

  public void testInlineCommentKeepsRightId() throws IOException {
    for (String noun : new String[] {"사람", "학교"}) {
      assertEquals("'" + noun + " # memo'", rightId(noun, noun), rightId(noun + " # memo", noun));
    }
  }

  /** The effect: the morpheme after the noun gets another part of speech, and other terms. */
  public void testLineFormatKeepsAnalysis() throws IOException {
    // 사람이나 ("person or"): with the plain line, 이나 is tagged JX (a particle), a default stop tag
    assertEquals(terms("사람", "사람이나"), terms("사람 ", "사람이나"));
    assertEquals(tagged("사람", "사람이나"), tagged("사람 # memo", "사람이나"));
    assertEquals(tagged("학교", "학교가"), tagged("학교 ", "학교가"));
  }
}
