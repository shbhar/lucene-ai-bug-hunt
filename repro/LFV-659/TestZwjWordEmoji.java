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
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: a word directly followed by ZERO WIDTH JOINER and an Extended_Pictographic
 * emoji. Public API only (ICUTokenizer, DefaultICUTokenizerConfig).
 *
 * <p>Expected (the assertion): the word's letters survive in some token, as they do without the
 * ZWJ, with a space, or with the ZWJ but no emoji. The bug tests fail if the word vanishes; the
 * control tests must pass.
 */
public class TestZwjWordEmoji extends LuceneTestCase {

  private static final String ZWJ = "\u200D";
  private static final String THUMBS_UP = new String(Character.toChars(0x1F44D));
  private static final String TEARS_OF_JOY = new String(Character.toChars(0x1F602));
  // Malayalam "avan" spelled with the ZWJ chillu form: a, va, na, virama (ZWJ follows)
  private static final String MALAYALAM = "\u0D05\u0D35\u0D28\u0D4D";

  private static List<String> tokens(Tokenizer tok, String text) throws IOException {
    List<String> out = new ArrayList<>();
    CharTermAttribute term = tok.addAttribute(CharTermAttribute.class);
    TypeAttribute type = tok.addAttribute(TypeAttribute.class);
    tok.setReader(new StringReader(text));
    tok.reset();
    while (tok.incrementToken()) {
      out.add(term.toString() + "/" + type.type());
    }
    tok.end();
    tok.close();
    return out;
  }

  private static List<String> defaultTokens(String text) throws IOException {
    return tokens(new ICUTokenizer(), text);
  }

  private static List<String> noCjkTokens(String text) throws IOException {
    return tokens(new ICUTokenizer(new DefaultICUTokenizerConfig(false, true)), text);
  }

  private static String esc(String s) {
    StringBuilder sb = new StringBuilder();
    s.codePoints()
        .forEach(
            cp -> {
              if (cp >= 0x20 && cp < 0x7F) sb.appendCodePoint(cp);
              else sb.append(String.format("\\u{%X}", cp));
            });
    return sb.toString();
  }

  private static void assertSomeTokenContains(List<String> toks, String input, String needle) {
    for (String t : toks) {
      if (t.contains(needle)) return;
    }
    fail(
        "\""
            + esc(needle)
            + "\" of input \""
            + esc(input)
            + "\" is in no token; tokens="
            + esc(toks.toString()));
  }

  // ---------------- bug cases (expected to fail on affected versions) ----------------

  public void testLatinWordZwjEmojiDefault() throws Exception {
    String in = "abc" + ZWJ + THUMBS_UP;
    assertSomeTokenContains(defaultTokens(in), in, "abc");
  }

  public void testLatinWordZwjEmojiInSentence() throws Exception {
    // "world" sits in the middle of normal text
    String in = "hello world" + ZWJ + THUMBS_UP + " again";
    List<String> toks = defaultTokens(in);
    assertSomeTokenContains(toks, in, "hello");
    assertSomeTokenContains(toks, in, "again");
    assertSomeTokenContains(toks, in, "world");
  }

  public void testMalayalamZwjEmojiDefault() throws Exception {
    String in = MALAYALAM + ZWJ + TEARS_OF_JOY;
    assertSomeTokenContains(defaultTokens(in), in, MALAYALAM);
  }

  public void testMalayalamZwjEmojiNoCjkConfig() throws Exception {
    String in = MALAYALAM + ZWJ + TEARS_OF_JOY;
    assertSomeTokenContains(noCjkTokens(in), in, MALAYALAM);
  }

  public void testDigitsZwjEmojiDefault() throws Exception {
    String in = "2024" + ZWJ + THUMBS_UP;
    assertSomeTokenContains(defaultTokens(in), in, "2024");
  }

  public void testEmojiAlsoLost() throws Exception {
    String in = "abc" + ZWJ + THUMBS_UP;
    assertSomeTokenContains(defaultTokens(in), in, THUMBS_UP);
  }

  // ---------------- controls (must pass everywhere) ----------------

  public void testControlNoZwj() throws Exception {
    String in = "abc" + THUMBS_UP;
    List<String> toks = defaultTokens(in);
    assertEquals(List.of("abc/<ALPHANUM>", THUMBS_UP + "/<EMOJI>"), toks);
  }

  public void testControlSpace() throws Exception {
    String in = "abc " + THUMBS_UP;
    List<String> toks = defaultTokens(in);
    assertEquals(List.of("abc/<ALPHANUM>", THUMBS_UP + "/<EMOJI>"), toks);
  }

  public void testControlZwjNoEmoji() throws Exception {
    String in = "abc" + ZWJ;
    assertSomeTokenContains(defaultTokens(in), in, "abc");
    String ml = MALAYALAM + ZWJ;
    assertSomeTokenContains(defaultTokens(ml), ml, MALAYALAM);
  }

  public void testControlZwjEmojiStartingWithEmoji() throws Exception {
    // emoji ZWJ sequence starting with an emoji is kept (upstream testEmojiSequence analogue)
    String in = TEARS_OF_JOY + ZWJ + THUMBS_UP;
    assertEquals(List.of(in + "/<EMOJI>"), defaultTokens(in));
  }
}
