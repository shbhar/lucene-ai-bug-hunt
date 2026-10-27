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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.KoreanAnalyzer;
import org.apache.lucene.analysis.ko.KoreanPartOfSpeechStopFilter;
import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.analysis.ko.POS;
import org.apache.lucene.analysis.ko.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.fst.FST;

/**
 * A noun from the user dictionary must connect to the next morpheme like a plain common noun (NNG)
 * of the bundled dictionary with the same last syllable: its context ids must be the bundled
 * dictionary's NNG context ids.
 */
public class TestUserNounContextIds extends LuceneTestCase {

  private static UserDictionary dict(String noun) throws IOException {
    return UserDictionary.open(new StringReader(noun));
  }

  /** "leftId/rightId" of the only entry of a one-line user dictionary. */
  private static String userIds(String noun) throws IOException {
    UserDictionary dict = dict(noun);
    List<Integer> wordIds = dict.lookup(noun.toCharArray(), 0, noun.length());
    assertEquals(1, wordIds.size());
    UserMorphData data = dict.getMorphAttributes();
    return data.getLeftId(wordIds.get(0)) + "/" + data.getRightId(wordIds.get(0));
  }

  /**
   * "leftId/rightId" of each NNG entry of the bundled dictionary for {@code noun}: the generic one
   * and any semantic subclass.
   */
  private static List<String> bundledNngIds(String noun) throws IOException {
    TokenInfoDictionary dict = TokenInfoDictionary.getInstance();
    TokenInfoFST fst = dict.getFST();
    FST.BytesReader reader = fst.getBytesReader();
    FST.Arc<Long> arc = fst.getFirstArc(new FST.Arc<>());
    long output = 0;
    for (int i = 0; i < noun.length(); i++) {
      assertNotNull(fst.findTargetArc(noun.charAt(i), arc, arc, i == 0, reader));
      output += arc.output();
    }
    assertTrue(arc.isFinal());
    output += arc.nextFinalOutput();
    IntsRef wordIds = new IntsRef();
    dict.lookupWordIds((int) output, wordIds);
    TokenInfoMorphData data = dict.getMorphAttributes();
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < wordIds.length; i++) {
      int wordId = wordIds.ints[wordIds.offset + i];
      if (data.getLeftPOS(wordId) == POS.Tag.NNG && data.getPOSType(wordId) == POS.Type.MORPHEME) {
        ids.add(data.getLeftId(wordId) + "/" + data.getRightId(wordId));
      }
    }
    return ids;
  }

  /** term/tag of each token (KoreanTokenizer, DecompoundMode.NONE); {@code dict} may be null. */
  private static String tagged(UserDictionary dict, String text) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (KoreanTokenizer tokenizer =
        new KoreanTokenizer(
            TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
            dict,
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

  /** The terms KoreanAnalyzer indexes (default decompound mode and stop tags). */
  private static List<String> terms(UserDictionary dict, String text) throws IOException {
    List<String> terms = new ArrayList<>();
    try (Analyzer analyzer =
            new KoreanAnalyzer(
                dict,
                KoreanTokenizer.DEFAULT_DECOMPOUND,
                KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS,
                false);
        TokenStream ts = analyzer.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        terms.add(term.toString());
      }
      ts.end();
    }
    return terms;
  }

  public void testUserNounGetsBundledNngContextIds() throws IOException {
    // 학교 ("school") ends without a final consonant, 학생 ("student") with one
    for (String noun : new String[] {"학교", "학생"}) {
      List<String> bundled = bundledNngIds(noun);
      String user = userIds(noun);
      assertTrue(noun + ": user " + user + ", bundled NNG " + bundled, bundled.contains(user));
    }
  }

  public void testParticleAfterUserNoun() throws IOException {
    // control: from the bundled dictionary, 가 after 학교 and 이 after 학생 are the subject particle
    assertEquals("학교/NNG 가/JKS", tagged(null, "학교가"));
    assertEquals("학생/NNG 이/JKS", tagged(null, "학생이"));
    // the same nouns from a one-line user dictionary must give the same reading
    assertEquals(tagged(null, "학교가"), tagged(dict("학교"), "학교가"));
    assertEquals(tagged(null, "학생이"), tagged(dict("학생"), "학생이"));
    // control: an object particle is read the same either way
    assertEquals(tagged(null, "학교를"), tagged(dict("학교"), "학교를"));
  }

  public void testKoreanAnalyzerTermsAfterUserNoun() throws IOException {
    // the subject particle is a stop tag, so only the noun is indexed
    assertEquals(List.of("학교"), terms(null, "학교가"));
    assertEquals(List.of("학교"), terms(dict("학교"), "학교가"));
    assertEquals(List.of("학생"), terms(dict("학생"), "학생이"));
    // a new word that the bundled dictionary does not know: "깃허브가 있다" ("there is a GitHub")
    assertEquals(List.of("깃허브", "있"), terms(dict("깃허브"), "깃허브가 있다"));
  }
}
