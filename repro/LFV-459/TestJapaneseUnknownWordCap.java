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
package org.apache.lucene.analysis.ja;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ja.JapaneseTokenizer.Mode;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.UnicodeUtil;

/**
 * An unknown word ends after 1024 UTF-16 chars (MAX_UNKNOWN_WORD_LENGTH). That cut must not fall
 * between the two chars of a supplementary character (see testSurrogates2: "we don't ever split
 * supplementaries").
 */
public class TestJapaneseUnknownWordCap extends BaseTokenStreamTestCase {

  private static final String EMOJI = "\uD83D\uDE00"; // U+1F600, two UTF-16 chars

  /** U+1F441 U+FE0F (eye + variation selector 16): one emoji of 3 UTF-16 chars. */
  private static final String EYE = "\uD83D\uDC41\uFE0F";

  /** 1200 chars of Thai without spaces (one unknown word class) with the emoji at char pos. */
  private static String thaiWithEmojiAt(int pos) {
    String thai = "ภาษาไทย".repeat(200);
    return thai.substring(0, pos) + EMOJI + thai.substring(pos, 1198);
  }

  private static List<String> tokens(Mode mode, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (Tokenizer t = new JapaneseTokenizer(newAttributeFactory(), null, true, mode)) {
      CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
      OffsetAttribute offset = t.addAttribute(OffsetAttribute.class);
      t.setReader(new StringReader(text));
      t.reset();
      while (t.incrementToken()) {
        String valid = UnicodeUtil.validUTF16String(term) ? "" : " NOT VALID UTF-16";
        out.add(
            "term ["
                + offset.startOffset()
                + ","
                + offset.endOffset()
                + "), length "
                + term.length()
                + valid);
        if (term.toString().equals(EMOJI)) {
          out.add("emoji [" + offset.startOffset() + "," + offset.endOffset() + ")");
        }
      }
      t.end();
    }
    return out;
  }

  public void testCapDoesNotSplitSurrogatePair() throws IOException {
    List<String> broken = new ArrayList<>();
    for (Mode mode : Mode.values()) {
      for (int pos = 1020; pos <= 1026; pos++) {
        for (String token : tokens(mode, thaiWithEmojiAt(pos))) {
          if (token.endsWith("NOT VALID UTF-16")) {
            broken.add(mode + ", emoji at char " + pos + ": " + token);
          }
        }
      }
      for (String token : tokens(mode, EYE.repeat(400))) {
        if (token.endsWith("NOT VALID UTF-16")) {
          broken.add(mode + ", 400 x (U+1F441 U+FE0F): " + token);
        }
      }
    }
    assertTrue(String.join("\n", broken), broken.isEmpty());
  }

  /** EXTENDED mode emits unknown words as unigrams; the emoji at char 1023 must be one of them. */
  public void testExtendedModeKeepsEmojiAtCap() throws IOException {
    for (int pos : new int[] {1022, 1023, 1024}) {
      List<String> tokens = tokens(Mode.EXTENDED, thaiWithEmojiAt(pos));
      assertTrue(
          "emoji at char " + pos + " is not a token",
          tokens.contains("emoji [" + pos + "," + (pos + 2) + ")"));
    }
  }
}
