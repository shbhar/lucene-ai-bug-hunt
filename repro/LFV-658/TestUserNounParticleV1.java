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

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ko.dict.UserDictionary;
import org.apache.lucene.analysis.ko.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A user-dictionary entry for a plain noun is documented (UserDictionary/UserMorphData comments) to
 * use the "NNG" left context and the "NNG with/without a coda on the last char" right context. A
 * user entry for a noun that the system dictionary already knows as a plain noun should therefore
 * not change how a grammatical particle after it is analysed.
 *
 * <p>Only public API: UserDictionary.open, KoreanTokenizer, KoreanAnalyzer, PartOfSpeechAttribute.
 */
public class TestUserNounParticleV1 extends LuceneTestCase {

  // noun + grammatical particle for that noun's final syllable.
  // no coda: 가 (subject) 는 (topic) 를 (object) 와 (and) 로 (to)
  // coda:    이 (subject) 은 (topic) 을 (object) 과 (and) 으로 (to)
  private static final String[] NO_CODA_NOUNS = {"학교", "나무", "친구", "사과"};
  private static final String[] CODA_NOUNS = {"학생", "가방", "사람", "책상"};
  private static final String[] NO_CODA_PARTICLES = {"가", "는", "를", "와", "로"};
  private static final String[] CODA_PARTICLES = {"이", "은", "을", "과", "으로"};

  private static List<String> analyze(UserDictionary dict, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (Tokenizer tok =
        new KoreanTokenizer(
            TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
            dict,
            KoreanTokenizer.DecompoundMode.NONE,
            false)) {
      CharTermAttribute term = tok.addAttribute(CharTermAttribute.class);
      PartOfSpeechAttribute pos = tok.addAttribute(PartOfSpeechAttribute.class);
      tok.setReader(new StringReader(text));
      tok.reset();
      while (tok.incrementToken()) {
        out.add(term.toString() + "/" + pos.getLeftPOS() + (pos.getPOSType() == POS.Type.MORPHEME ? "" : "(" + pos.getPOSType() + ")"));
      }
      tok.end();
    }
    return out;
  }

  private static List<String> indexedTerms(UserDictionary dict, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (Analyzer a =
            new KoreanAnalyzer(
                dict,
                KoreanTokenizer.DEFAULT_DECOMPOUND,
                KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS,
                false);
        TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  private static List<String> probes() {
    List<String> p = new ArrayList<>();
    for (String n : NO_CODA_NOUNS) for (String j : NO_CODA_PARTICLES) p.add(n + "\t" + j);
    for (String n : CODA_NOUNS) for (String j : CODA_PARTICLES) p.add(n + "\t" + j);
    return p;
  }

  /** Token analysis (term/POS) of noun+particle must not change when the noun is a user entry. */
  public void testParticleAnalysisUnchangedByUserNoun() throws IOException {
    StringBuilder diffs = new StringBuilder();
    int n = 0, d = 0;
    for (String probe : probes()) {
      String noun = probe.split("\t")[0];
      String text = noun + probe.split("\t")[1];
      UserDictionary user = UserDictionary.open(new StringReader(noun + "\n"));
      List<String> sys = analyze(null, text);
      List<String> usr = analyze(user, text);
      if (sys.size() == 1) {
        // the system dictionary has a longer word spanning noun+particle (e.g. a compound); a user
        // entry takes precedence at that start position by design, so this probe says nothing.
        System.out.println("SKIP " + text + " system=" + sys);
        continue;
      }
      n++;
      System.out.println("PROBE " + text + " system=" + sys + " user=" + usr + (sys.equals(usr) ? "" : "  <-- DIFF"));
      if (!sys.equals(usr)) {
        d++;
        diffs.append(text).append(": system noun ").append(sys).append(" | user noun ").append(usr).append('\n');
      }
    }
    System.out.println("SUMMARY analysis probes=" + n + " differing=" + d);
    assertEquals("particle analysis changed by a user entry for the same noun (" + d + "/" + n + "):\n" + diffs, 0, d);
  }

  /** KoreanAnalyzer (default stop tags) must index the same terms with and without the user noun. */
  public void testIndexedTermsUnchangedByUserNoun() throws IOException {
    StringBuilder diffs = new StringBuilder();
    int n = 0, d = 0;
    for (String probe : probes()) {
      String noun = probe.split("\t")[0];
      String text = noun + probe.split("\t")[1];
      UserDictionary user = UserDictionary.open(new StringReader(noun + "\n"));
      if (analyze(null, text).size() == 1) {
        continue; // system word spans noun+particle: see testParticleAnalysisUnchangedByUserNoun
      }
      List<String> sys = indexedTerms(null, text);
      List<String> usr = indexedTerms(user, text);
      n++;
      if (!sys.equals(usr)) {
        d++;
        diffs.append(text).append(": ").append(sys).append(" vs ").append(usr).append('\n');
      }
    }
    System.out.println("SUMMARY analyzer probes=" + n + " differing=" + d + "\n" + diffs);
    assertEquals("KoreanAnalyzer terms changed by a user entry (" + d + "/" + n + "):\n" + diffs, 0, d);
  }

  /**
   * Out-of-vocabulary user nouns (not in the system dictionary): the subject particle that matches
   * the noun's final syllable must be analysed as a subject particle (JKS).
   */
  public void testSubjectParticleAfterNewUserNoun() throws IOException {
    // 루씬 ends with a coda (ㄴ) -> subject particle 이; 노리베 ends without a coda -> subject particle 가
    UserDictionary user = UserDictionary.open(new StringReader("루씬\n노리베\n"));
    List<String> a = analyze(user, "루씬이");
    List<String> b = analyze(user, "노리베가");
    System.out.println("OOV 루씬이=" + a + " 노리베가=" + b);
    // the particle must come out as a particle (J*: JKS on the 2.1.1 dictionary, J on 2.0.3)
    StringBuilder bad = new StringBuilder();
    if (a.size() != 2 || !a.get(0).equals("루씬/NNG") || !a.get(1).startsWith("이/J")) {
      bad.append("루씬이 -> ").append(a).append('\n');
    }
    if (b.size() != 2 || !b.get(0).equals("노리베/NNG") || !b.get(1).startsWith("가/J")) {
      bad.append("노리베가 -> ").append(b).append('\n');
    }
    assertEquals("subject particle after a user noun:\n" + bad, "", bad.toString());
  }
}
