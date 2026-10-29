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

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.lang.UScript;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ClasspathResourceLoader;

/**
 * Independent check: with myanmarAsWords=false ("Myanmar text ... will be tokenized as syllables")
 * a letter that the default (myanmarAsWords=true) mode puts into a token should still be in some
 * token. Public API only: ICUTokenizer, DefaultICUTokenizerConfig, ICUTokenizerFactory.
 */
public class TestSyllableModeKeepsLetters extends LuceneTestCase {

  private static final int KA = 0x1000; // MYANMAR LETTER KA

  private static List<String> tokens(Tokenizer t, String text) throws IOException {
    List<String> out = new ArrayList<>();
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    t.setReader(new StringReader(text));
    t.reset();
    while (t.incrementToken()) {
      out.add(term.toString());
    }
    t.end();
    t.close();
    return out;
  }

  private static List<String> syllableMode(String text) throws IOException {
    return tokens(new ICUTokenizer(new DefaultICUTokenizerConfig(true, false)), text);
  }

  private static List<String> wordMode(String text) throws IOException {
    return tokens(new ICUTokenizer(new DefaultICUTokenizerConfig(true, true)), text);
  }

  private static boolean covered(List<String> toks, int cp) {
    String s = new String(Character.toChars(cp));
    for (String tok : toks) {
      if (tok.contains(s)) {
        return true;
      }
    }
    return false;
  }

  private static String hex(int cp) {
    return String.format(java.util.Locale.ROOT, "U+%04X", cp);
  }

  private static String str(int... cps) {
    return new String(cps, 0, cps.length);
  }

  /** KA + MYANMAR MODIFIER LETTER SHAN SAWAW (U+A9E6, gc=Lm, sc=Mymr). */
  public void testShanModifierLetterAfterConsonant() throws Exception {
    String text = str(KA, 0xA9E6);
    List<String> word = wordMode(text);
    List<String> syl = syllableMode(text);
    System.out.println("word mode " + word + " / syllable mode " + syl);
    assertTrue("control: word mode keeps U+A9E6: " + word, covered(word, 0xA9E6));
    assertTrue("syllable mode lost U+A9E6, tokens=" + syl, covered(syl, 0xA9E6));
  }

  /** KA + MYANMAR MODIFIER LETTER KHAMTI REDUPLICATION (U+AA70, gc=Lm, sc=Mymr). */
  public void testKhamtiReduplicationAfterConsonant() throws Exception {
    String text = str(KA, 0xAA70);
    List<String> word = wordMode(text);
    List<String> syl = syllableMode(text);
    System.out.println("word mode " + word + " / syllable mode " + syl);
    assertTrue("control: word mode keeps U+AA70: " + word, covered(word, 0xAA70));
    assertTrue("syllable mode lost U+AA70, tokens=" + syl, covered(syl, 0xAA70));
  }

  /** The same through the SPI factory option myanmarAsWords="false". */
  public void testFactoryOptionShan() throws Exception {
    Map<String, String> args = new HashMap<>();
    args.put("myanmarAsWords", "false");
    ICUTokenizerFactory f = new ICUTokenizerFactory(args);
    f.inform(new ClasspathResourceLoader(getClass()));
    String text = str(KA, 0xA9E6, 0x20, KA, 0xAA70);
    List<String> syl = tokens(f.create(), text);
    System.out.println("factory myanmarAsWords=false " + syl);
    assertTrue("factory lost U+A9E6, tokens=" + syl, covered(syl, 0xA9E6));
    assertTrue("factory lost U+AA70, tokens=" + syl, covered(syl, 0xAA70));
  }

  /**
   * MICRO SIGN (U+00B5, sc=Common, gc=Ll) after a Myanmar word and a space: it stays in the Myanmar
   * run (Common script), where word mode keeps it and syllable mode does not.
   */
  public void testCommonLetterInMyanmarRun() throws Exception {
    // KA, E, AA, NGA, ASAT, VISARGA (a syllable "kaung:"), SPACE, MICRO SIGN
    String text = str(KA, 0x1031, 0x102C, 0x1004, 0x103A, 0x1038, 0x20, 0xB5);
    List<String> word = wordMode(text);
    List<String> syl = syllableMode(text);
    System.out.println("word mode " + word + " / syllable mode " + syl);
    assertTrue("control: word mode keeps U+00B5: " + word, covered(word, 0xB5));
    assertTrue("syllable mode lost U+00B5, tokens=" + syl, covered(syl, 0xB5));
  }

  /**
   * Sweep: every assigned code point with Script=Myanmar and a letter general category, isolated
   * and after KA. Reports those that word mode keeps and syllable mode drops.
   */
  public void testSweepMyanmarLetters() throws Exception {
    List<String> lost = new ArrayList<>();
    int checked = 0;
    for (int cp = 0; cp <= 0x10FFFF; cp++) {
      if (UScript.getScript(cp) != UScript.MYANMAR || !UCharacter.isLetter(cp)) {
        continue;
      }
      checked++;
      for (String ctx : new String[] {"iso", "afterKA"}) {
        String text = ctx.equals("iso") ? str(cp) : str(KA, cp);
        List<String> word = wordMode(text);
        List<String> syl = syllableMode(text);
        if (covered(word, cp) && !covered(syl, cp)) {
          lost.add(
              hex(cp)
                  + " "
                  + UCharacter.getName(cp)
                  + " gc="
                  + UCharacter.getPropertyValueName(
                      UProperty.GENERAL_CATEGORY,
                      UCharacter.getType(cp),
                      UProperty.NameChoice.SHORT)
                  + " ["
                  + ctx
                  + "] word="
                  + word
                  + " syl="
                  + syl);
        }
      }
    }
    System.out.println("checked " + checked + " Myanmar-script letters; lost in syllable mode:");
    for (String l : lost) {
      System.out.println("  " + l);
    }
    assertTrue("Myanmar-script letters lost only in syllable mode: " + lost, lost.isEmpty());
  }
}
