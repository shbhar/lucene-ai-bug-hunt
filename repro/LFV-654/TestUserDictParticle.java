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
package org.apache.lucene.analysis.ko;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.KoreanTokenizer.DecompoundMode;
import org.apache.lucene.analysis.ko.dict.UserDictionary;
import org.apache.lucene.analysis.ko.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.Test;

/**
 * Public API only (UserDictionary.open + KoreanTokenizer), portable across 9.x and 10.x. The same
 * user noun written on differently formatted lines should give the same analysis of the text that
 * follows it.
 */
public class TestUserDictParticle {

  static UserDictionary dict(String line) throws IOException {
    return UserDictionary.open(new StringReader(line + "\n"));
  }

  static String analyze(UserDictionary dict, String text) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (KoreanTokenizer tok =
        new KoreanTokenizer(
            TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY, dict, DecompoundMode.NONE, false, true)) {
      CharTermAttribute term = tok.addAttribute(CharTermAttribute.class);
      PartOfSpeechAttribute pos = tok.addAttribute(PartOfSpeechAttribute.class);
      tok.setReader(new StringReader(text));
      tok.reset();
      while (tok.incrementToken()) {
        sb.append(term).append('/').append(pos.getLeftPOS()).append(' ');
      }
      tok.end();
    }
    return sb.toString().trim();
  }

  static void compare(
      List<String> diffs, String baseLine, String variantLine, String[] texts) throws IOException {
    UserDictionary base = dict(baseLine);
    UserDictionary variant = dict(variantLine);
    for (String text : texts) {
      String a = analyze(base, text);
      String b = analyze(variant, text);
      if (!a.equals(b)) {
        diffs.add(
            "'"
                + text
                + "': ["
                + baseLine
                + "] -> "
                + a
                + "  |  ["
                + variantLine.replace("\t", "\\t")
                + "] -> "
                + b);
      }
    }
  }

  static final String[] PARTICLES = {"이", "가", "은", "는", "을", "를", "과", "와"};

  static String[] withParticles(String noun) {
    String[] r = new String[PARTICLES.length];
    for (int i = 0; i < PARTICLES.length; i++) {
      r[i] = noun + PARTICLES[i];
    }
    return r;
  }

  /** Control: no whitespace left at the end of the line. Expected to pass. */
  @Test
  public void testControlCommentWithoutSpace() throws IOException {
    List<String> diffs = new ArrayList<>();
    for (String noun : new String[] {"학교", "나무", "사과", "사람", "책상"}) {
      compare(diffs, noun, noun + "# memo", withParticles(noun));
    }
    compare(diffs, "세종시", "세종시 세종 시", withParticles("세종시"));
    assertTrue(String.join("\n", diffs), diffs.isEmpty());
  }

  @Test
  public void testTrailingSpaceOrInlineComment() throws IOException {
    List<String> diffs = new ArrayList<>();
    for (String noun : new String[] {"학교", "나무", "사과", "사람", "책상"}) {
      compare(diffs, noun, noun + " ", withParticles(noun));
      compare(diffs, noun, noun + " # memo", withParticles(noun));
    }
    assertTrue(String.join("\n", diffs), diffs.isEmpty());
  }

  @Test
  public void testShorterSegmentation() throws IOException {
    List<String> diffs = new ArrayList<>();
    compare(diffs, "세종시 세종 시", "세종시 세종", withParticles("세종시"));
    assertTrue(String.join("\n", diffs), diffs.isEmpty());
  }

  /** Indexed terms of KoreanAnalyzer (default decompound mode and default POS stop tags). */
  static String terms(UserDictionary dict, String text) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (KoreanAnalyzer a =
            new KoreanAnalyzer(
                dict,
                KoreanTokenizer.DEFAULT_DECOMPOUND,
                KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS,
                false);
        TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        sb.append('[').append(term).append(']');
      }
      ts.end();
    }
    return sb.toString();
  }

  /**
   * End-to-end on well-formed text: the indexed terms of KoreanAnalyzer, trailing-space entry vs
   * plain entry. ("사람이나" = "person or", "책상이나" = "desk or", "학생이나" = "student or",
   * "학교가방을 샀다" = "bought a school bag").
   */
  @Test
  public void testAnalyzerTermsTrailingSpace() throws IOException {
    List<String> diffs = new ArrayList<>();
    String[][] pairs = {{"학교", "학교 "}, {"사람", "사람 "}, {"책상", "책상 "}, {"학생", "학생 "}};
    String[] texts = {"사람이나", "책상이나", "학생이나", "학교가방을 샀다"};
    for (String[] p : pairs) {
      UserDictionary a = dict(p[0]);
      UserDictionary b = dict(p[1]);
      for (String text : texts) {
        String ta = terms(a, text);
        String tb = terms(b, text);
        if (!ta.equals(tb)) {
          diffs.add("'" + text + "': [" + p[0] + "] -> " + ta + "  |  [" + p[1] + "] -> " + tb);
        }
      }
    }
    assertTrue(String.join("\n", diffs), diffs.isEmpty());
  }
}
