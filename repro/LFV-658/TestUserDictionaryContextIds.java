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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.dict.UserDictionary;
import org.apache.lucene.analysis.ko.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.Test;

/**
 * A user-dictionary noun is meant to connect to what follows like a common noun (NNG) whose last
 * syllable has, or has not, a final consonant: UserDictionary picks RIGHT_ID_T ("NNG right with
 * hangul and a coda on the last char") or RIGHT_ID_F ("... no coda") and UserMorphData uses LEFT_ID
 * ("NNG left"). So adding a noun that the system dictionary already knows as a plain NNG to the
 * user dictionary must not change how the particle after it is analysed.
 *
 * <p>The test compares, for a few plain nouns followed by a particle, the part of speech of the
 * particle (KoreanTokenizer) and the KoreanAnalyzer terms with and without a user dictionary
 * containing just that noun.
 */
public class TestUserDictionaryContextIds {

  // vowel-final (no final consonant) and consonant-final plain nouns of the bundled dictionary,
  // followed by the subject particle 가/이 or the conjunctive/comitative particle 와
  private static final String[] TEXTS = {"학교가", "학교이", "나무가", "나무와", "학생이", "학생가", "가방이"};

  private static String particlePos(UserDictionary dict, String text) throws IOException {
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
      int i = 0;
      while (tok.incrementToken()) {
        if (i++ > 0) { // skip the noun itself: its tag is NNG either way
          sb.append(term).append('/').append(pos.getLeftPOS()).append(' ');
        }
      }
      tok.end();
    }
    return sb.toString();
  }

  private static List<String> terms(Analyzer a, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  @Test
  public void testParticleAfterUserNounMatchesSystemNoun() throws IOException {
    StringBuilder diffs = new StringBuilder();
    for (String text : TEXTS) {
      String noun = text.substring(0, 2);
      UserDictionary dict = UserDictionary.open(new StringReader(noun));
      String withoutUser = particlePos(null, text);
      String withUser = particlePos(dict, text);
      if (!withoutUser.equals(withUser)) {
        diffs.append(text)
            .append(": system noun -> ")
            .append(withoutUser)
            .append("| user noun -> ")
            .append(withUser)
            .append('\n');
      }
    }
    assertEquals("particle analysis changed by a user entry for the same plain noun:\n" + diffs, "", diffs.toString());
  }

  @Test
  public void testKoreanAnalyzerTermsUnchangedByUserNoun() throws IOException {
    StringBuilder diffs = new StringBuilder();
    for (String text : TEXTS) {
      String noun = text.substring(0, 2);
      UserDictionary dict = UserDictionary.open(new StringReader(noun));
      List<String> withoutUser, withUser;
      try (Analyzer plain =
              new KoreanAnalyzer(
                  null,
                  KoreanTokenizer.DEFAULT_DECOMPOUND,
                  KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS,
                  false);
          Analyzer user =
              new KoreanAnalyzer(
                  dict,
                  KoreanTokenizer.DEFAULT_DECOMPOUND,
                  KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS,
                  false)) {
        withoutUser = terms(plain, text);
        withUser = terms(user, text);
      }
      if (!withoutUser.equals(withUser)) {
        diffs.append(text).append(": ").append(withoutUser).append(" vs ").append(withUser).append('\n');
      }
    }
    assertEquals("KoreanAnalyzer terms changed by a user entry for the same plain noun:\n" + diffs, "", diffs.toString());
  }
}
