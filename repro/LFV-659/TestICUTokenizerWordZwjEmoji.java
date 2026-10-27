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

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * A word directly followed by ZERO WIDTH JOINER (U+200D) and an emoji must not vanish from the
 * tokens, together with the emoji. With a space instead of the ZWJ, or without the ZWJ, both are
 * tokens.
 */
public class TestICUTokenizerWordZwjEmoji extends BaseTokenStreamTestCase {

  private static final String ZWJ = "\u200D";
  private static final String THUMBS_UP = "👍"; // U+1F44D
  private static final String TEARS_OF_JOY = "😂"; // U+1F602

  private static List<String> tokens(boolean cjkAsWords, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (ICUTokenizer tokenizer =
        new ICUTokenizer(new DefaultICUTokenizerConfig(cjkAsWords, true))) {
      CharTermAttribute term = tokenizer.addAttribute(CharTermAttribute.class);
      tokenizer.setReader(new StringReader(text));
      tokenizer.reset();
      while (tokenizer.incrementToken()) {
        out.add(term.toString());
      }
      tokenizer.end();
    }
    return out;
  }

  /**
   * Each part of the text must be inside some token, with the default and the other config.
   * (escape() is BaseTokenStreamTestCase's.)
   */
  private static void assertKept(String text, String... parts) throws IOException {
    for (boolean cjkAsWords : new boolean[] {true, false}) {
      List<String> tokens = tokens(cjkAsWords, text);
      for (String part : parts) {
        boolean found = false;
        for (String token : tokens) {
          found |= token.contains(part);
        }
        assertTrue(
            String.format(
                Locale.ROOT,
                "cjkAsWords=%s: \"%s\" of \"%s\" is in no token; tokens: %s",
                cjkAsWords,
                escape(part),
                escape(text),
                escape(tokens.toString())),
            found);
      }
    }
  }

  /** Controls: these pass on affected versions too. */
  public void testControls() throws Exception {
    assertEquals(List.of("abc", THUMBS_UP), tokens(true, "abc " + THUMBS_UP)); // space
    assertEquals(List.of("abc", THUMBS_UP), tokens(true, "abc" + THUMBS_UP)); // no ZWJ
    assertEquals(List.of("abc" + ZWJ), tokens(true, "abc" + ZWJ)); // ZWJ, no emoji
    // an emoji ZWJ sequence stays one token
    assertEquals(
        List.of(TEARS_OF_JOY + ZWJ + THUMBS_UP), tokens(true, TEARS_OF_JOY + ZWJ + THUMBS_UP));
  }

  public void testWordThenZwjEmoji() throws Exception {
    assertKept("abc" + ZWJ + THUMBS_UP, "abc", THUMBS_UP);
  }

  public void testWordThenZwjEmojiMidSentence() throws Exception {
    assertKept("hello world" + ZWJ + THUMBS_UP + " again", "hello", "world", THUMBS_UP, "again");
  }

  public void testNumberThenZwjEmoji() throws Exception {
    assertKept("2024" + ZWJ + THUMBS_UP, "2024", THUMBS_UP);
  }

  public void testIdeographsThenZwjEmoji() throws Exception {
    assertKept("foo 日本語" + ZWJ + THUMBS_UP + " bar", "foo", "語", THUMBS_UP);
  }

  /** Malayalam written with the older ZWJ form of a chillu letter: NA, VIRAMA, ZWJ. */
  public void testMalayalamZwjChilluThenEmoji() throws Exception {
    assertKept("\u0D05\u0D35\u0D28\u0D4D" + ZWJ + TEARS_OF_JOY, "\u0D05\u0D35\u0D28", TEARS_OF_JOY);
  }
}
