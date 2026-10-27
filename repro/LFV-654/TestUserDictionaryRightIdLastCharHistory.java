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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.lucene.analysis.ko.dict.UserDictionary;
import org.apache.lucene.analysis.ko.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.Test;

/**
 * Version-portable form of TestUserDictionaryRightIdLastChar, used to run the same check on every
 * release that ships nori (7.4.0 onward). Differences from the main repro: it uses the 4-argument
 * KoreanTokenizer constructor, which exists in every release (the 5-argument one with
 * discardPunctuation only exists since 8.2.0; the inputs contain no punctuation, so the flag does
 * not matter), and it also checks the right context id itself, read through reflection because the
 * accessor moved from UserDictionary (7.x-9.x) to UserDictionary.getMorphAttributes() (10.x).
 *
 * <p>Claim: a user noun's right context id depends on whether the surface form's last syllable has
 * a final consonant, so it must not change when the same surface form is written with a trailing
 * space, with an inline comment (the comment is removed and leaves the space), or with a
 * segmentation that does not end with the surface form's last syllable.
 */
public class TestUserDictionaryRightIdLastCharHistory {

  private static final String[] NO_CODA = {"학교", "나무", "사과"};
  private static final String[] CODA = {"사람", "책상"};

  private static UserDictionary dict(String line) throws IOException {
    return UserDictionary.open(new StringReader(line + "\n"));
  }

  private static String analysis(String entry, String text) throws IOException {
    UserDictionary dict = dict(entry);
    StringBuilder sb = new StringBuilder();
    try (KoreanTokenizer tok =
        new KoreanTokenizer(
            KoreanTokenizer.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
            dict,
            KoreanTokenizer.DecompoundMode.NONE,
            false)) {
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

  /** Right context id of the single entry {@code line}, looked up by its surface form. */
  private static int rightId(String line, String surface) throws Exception {
    UserDictionary dict = dict(line);
    char[] chars = surface.toCharArray();
    List<Integer> ids = dict.lookup(chars, 0, chars.length);
    assertEquals("one match for " + surface + " in [" + line + "]", 1, ids.size());
    int wordId = ids.get(0);
    try {
      Method m = UserDictionary.class.getMethod("getRightId", int.class);
      return ((Number) m.invoke(dict, wordId)).intValue();
    } catch (NoSuchMethodException e) {
      Object morph = UserDictionary.class.getMethod("getMorphAttributes").invoke(dict);
      Method m = morph.getClass().getMethod("getRightId", int.class);
      m.setAccessible(true);
      return ((Number) m.invoke(morph, wordId)).intValue();
    }
  }

  private static String constant(String name) {
    try {
      Field f = UserDictionary.class.getDeclaredField(name);
      f.setAccessible(true);
      return String.valueOf(f.get(null));
    } catch (ReflectiveOperationException e) {
      return "?";
    }
  }

  @Test
  public void testTrailingCommentOrSpaceDoesNotChangeAnalysis() throws IOException {
    StringBuilder diffs = new StringBuilder();
    for (String noun : NO_CODA) {
      String text = noun + "이";
      String expected = analysis(noun, text);
      for (String variant : new String[] {noun + " # memo", noun + " "}) {
        String actual = analysis(variant, text);
        if (!expected.equals(actual)) {
          diffs.append("[").append(noun).append("] ").append(text).append(" -> ").append(expected)
              .append(" | [").append(variant).append("] -> ").append(actual).append('\n');
        }
      }
    }
    assertEquals(diffs.toString(), "", diffs.toString());
  }

  @Test
  public void testShorterSegmentationDoesNotChangeAnalysis() throws IOException {
    assertEquals(analysis("세종시 세종 시", "세종시가"), analysis("세종시 세종", "세종시가"));
  }

  @Test
  public void testRightIdIgnoresTrailingCommentOrSpace() throws Exception {
    StringBuilder diffs = new StringBuilder();
    for (String[] group : new String[][] {NO_CODA, CODA}) {
      for (String noun : group) {
        int base = rightId(noun, noun);
        for (String variant : new String[] {noun + " ", noun + " # memo"}) {
          int v = rightId(variant, noun);
          if (v != base) {
            diffs.append("[").append(noun).append("] rightId=").append(base).append(" but [")
                .append(variant).append("] rightId=").append(v).append('\n');
          }
        }
      }
    }
    assertEquals(diffs.toString(), "", diffs.toString());
  }

  @Test
  public void testRightIdShorterSegmentation() throws Exception {
    assertEquals(
        "[세종시 세종 시] vs [세종시 세종]",
        rightId("세종시 세종 시", "세종시"),
        rightId("세종시 세종", "세종시"));
  }

  /** Controls that leave no whitespace at the end of the line: expected to pass on every version. */
  @Test
  public void testControls() throws Exception {
    assertEquals(rightId("학교", "학교"), rightId("학교# memo", "학교"));
    assertEquals(rightId("사람", "사람"), rightId("사람# memo", "사람"));
    assertEquals(rightId("세종시", "세종시"), rightId("세종시 세종 시", "세종시"));
    assertEquals(analysis("학교", "학교이"), analysis("학교# memo", "학교이"));
    // the coda property itself is real: a coda noun and a no-coda noun get different ids
    assertTrue(rightId("학교", "학교") != rightId("사람", "사람"));
  }

  /** Prints the observed values for the version record. Never fails. */
  @Test
  public void testRecord() throws Exception {
    StringBuilder sb = new StringBuilder("RECORD");
    sb.append(" constants RIGHT_ID=").append(constant("RIGHT_ID"))
        .append(" RIGHT_ID_T=").append(constant("RIGHT_ID_T"))
        .append(" RIGHT_ID_F=").append(constant("RIGHT_ID_F")).append(" ;");
    for (String line : new String[] {"학교", "학교 ", "학교 # memo", "학교# memo", "사람", "사람 "}) {
      sb.append(" rightId[").append(line).append("]=").append(rightId(line, line.split("[ #]")[0]));
    }
    sb.append(" rightId[세종시 세종 시]=").append(rightId("세종시 세종 시", "세종시"));
    sb.append(" rightId[세종시 세종]=").append(rightId("세종시 세종", "세종시")).append(" ;");
    for (String line : new String[] {"학교", "학교 ", "학교 # memo"}) {
      sb.append(" 학교이[").append(line).append("]=").append(analysis(line, "학교이"));
    }
    for (String line : new String[] {"세종시 세종 시", "세종시 세종"}) {
      sb.append(" 세종시가[").append(line).append("]=").append(analysis(line, "세종시가"));
    }
    System.out.println(sb);
  }
}
