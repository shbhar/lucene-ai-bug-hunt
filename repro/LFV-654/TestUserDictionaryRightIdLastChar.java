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

import java.io.IOException;
import java.io.StringReader;
import org.apache.lucene.analysis.ko.dict.UserDictionary;
import org.apache.lucene.analysis.ko.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.Test;

/**
 * UserDictionary chooses a user noun's right context by whether "the last char" has a final
 * consonant (RIGHT_ID_T: "NNG right with hangul and a coda on the last char", RIGHT_ID_F: "... no
 * coda"), but it reads that char from the end of the whole entry line, not from the surface form.
 * So the same noun gets another right context, and the particle after it another analysis, when
 * the line ends with a trailing space or an inline comment ("학교 # note", the comment is removed
 * and leaves the space; the space is not hangul, so the generic NNG context is used), or with a
 * segmentation that does not end with the surface's last syllable
 * ("세종시 세종": the line ends with 종, which has a coda, while 세종시 ends with 시, which has none).
 */
public class TestUserDictionaryRightIdLastChar {

  private static String analysis(String entry, String text) throws IOException {
    UserDictionary dict = UserDictionary.open(new StringReader(entry));
    StringBuilder sb = new StringBuilder();
    try (KoreanTokenizer tok =
        new KoreanTokenizer(
            KoreanTokenizer.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
            dict,
            KoreanTokenizer.DecompoundMode.NONE,
            false,
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
    return sb.toString();
  }

  @Test
  public void testTrailingCommentOrSpaceDoesNotChangeAnalysis() throws IOException {
    // vowel-final plain nouns followed by 이 (copula or subject particle, depending on the context)
    String[][] cases = {{"학교", "학교이"}, {"나무", "나무이"}, {"사과", "사과이"}};
    StringBuilder diffs = new StringBuilder();
    for (String[] c : cases) {
      String expected = analysis(c[0], c[1]);
      for (String variant : new String[] {c[0] + " # memo", c[0] + " "}) {
        String actual = analysis(variant, c[1]);
        if (!expected.equals(actual)) {
          diffs.append('\'').append(c[0]).append("' -> ").append(expected).append("| '")
              .append(variant).append("' -> ").append(actual).append('\n');
        }
      }
    }
    assertEquals(diffs.toString(), "", diffs.toString());
  }

  @Test
  public void testShorterSegmentationDoesNotChangeRightContext() throws IOException {
    // both entries have the surface form 세종시; only how the segmentation is written differs
    assertEquals(analysis("세종시 세종 시", "세종시가"), analysis("세종시 세종", "세종시가"));
  }
}
