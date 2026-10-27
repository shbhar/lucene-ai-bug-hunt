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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * ICUTokenizer reads its input in 4096-char chunks and, when a chunk has no whitespace, uses the
 * whole chunk. If the chunk then ends with the high surrogate of a supplementary character, the
 * character is split across two chunks and silently disappears from the token stream.
 *
 * <p>The text is Chinese without whitespace (as Chinese text usually is). U+20000 (an Extension B
 * ideograph) and U+1F600 (an emoji) are tokens at any position except when their first UTF-16 unit
 * is char 4095 of the chunk.
 */
public class TestICUTokenizerRefillSurrogatePair extends BaseTokenStreamTestCase {

  private static final String EXT_B = new String(Character.toChars(0x20000));
  private static final String EMOJI = new String(Character.toChars(0x1F600));

  /** Chinese text of 'length' chars with no whitespace, with 'probe' inserted at char 'pos'. */
  private static String text(int pos, String probe, int length) {
    StringBuilder sb = new StringBuilder();
    while (sb.length() < pos) sb.append("中文");
    sb.setLength(pos);
    sb.append(probe);
    while (sb.length() < length) sb.append("中文");
    return sb.toString();
  }

  private static List<String> terms(Tokenizer tokenizer, String text) throws IOException {
    List<String> terms = new ArrayList<>();
    tokenizer.setReader(new StringReader(text));
    CharTermAttribute termAtt = tokenizer.addAttribute(CharTermAttribute.class);
    OffsetAttribute offsetAtt = tokenizer.addAttribute(OffsetAttribute.class);
    tokenizer.reset();
    while (tokenizer.incrementToken()) {
      terms.add(termAtt.toString());
    }
    tokenizer.end();
    assertEquals("final offset", text.length(), offsetAtt.endOffset());
    tokenizer.close();
    return terms;
  }

  private static void assertProbeIsAToken(String probe, int pos) throws IOException {
    String text = text(pos, probe, 4200);
    List<String> terms = terms(new ICUTokenizer(), text);
    assertTrue(
        "U+"
            + Integer.toHexString(probe.codePointAt(0)).toUpperCase(java.util.Locale.ROOT)
            + " at char "
            + pos
            + " of a "
            + text.length()
            + "-char text without whitespace is missing from the token stream",
        terms.contains(probe));
  }

  /** Controls: one char earlier or later, the character is a token. */
  public void testControlsAwayFromTheChunkEnd() throws IOException {
    for (int pos : new int[] {10, 4000, 4094, 4096}) {
      assertProbeIsAToken(EXT_B, pos);
      assertProbeIsAToken(EMOJI, pos);
    }
  }

  /** The Extension B ideograph whose high surrogate is the last char of the 4096-char chunk. */
  public void testExtensionBIdeographAtChunkEnd() throws IOException {
    assertProbeIsAToken(EXT_B, 4095);
  }

  /** The same for an emoji. */
  public void testEmojiAtChunkEnd() throws IOException {
    assertProbeIsAToken(EMOJI, 4095);
  }

  /** The same through the SPI factory ("icu") with default parameters. */
  public void testFactoryBuiltAnalyzer() throws IOException {
    try (Analyzer a = CustomAnalyzer.builder().withTokenizer("icu").build()) {
      String text = text(4095, EXT_B, 4200);
      List<String> terms = new ArrayList<>();
      try (var ts = a.tokenStream("f", text)) {
        CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
        ts.reset();
        while (ts.incrementToken()) terms.add(termAtt.toString());
        ts.end();
      }
      assertTrue("U+20000 at char 4095 is missing (factory-built analyzer)", terms.contains(EXT_B));
    }
  }
}
