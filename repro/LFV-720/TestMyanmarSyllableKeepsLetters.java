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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.ClasspathResourceLoader;

/**
 * With myanmarAsWords=false, Myanmar text "will be tokenized as syllables", but every letter must
 * still end up in some token, as in the default mode. U+A9E6 MYANMAR MODIFIER LETTER SHAN
 * REDUPLICATION and U+AA70 MYANMAR MODIFIER LETTER KHAMTI REDUPLICATION are the only Myanmar-script
 * letters that are not syllable bases.
 */
public class TestMyanmarSyllableKeepsLetters extends BaseTokenStreamTestCase {

  private static final String KA_SHAN = "ကꧦ"; // U+1000 KA, U+A9E6
  private static final String KA_KHAMTI = "ကꩰ"; // U+1000 KA, U+AA70
  private static final String SHAN_ALONE = "ꧦ"; // U+A9E6
  private static final String KAUNG_MICRO = "ကောင်း µm"; // "good", space, U+00B5 MICRO SIGN, m

  private Analyzer syllables;
  private Analyzer words;

  private static Analyzer icu(boolean myanmarAsWords) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(
            new ICUTokenizer(
                newAttributeFactory(), new DefaultICUTokenizerConfig(true, myanmarAsWords)));
      }
    };
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    syllables = icu(false);
    words = icu(true);
  }

  @Override
  public void tearDown() throws Exception {
    syllables.close();
    words.close();
    super.tearDown();
  }

  /** Fails if a letter or digit of {@code text} lies outside every token. */
  private static void assertKeepsEveryLetter(Analyzer a, String text) throws IOException {
    boolean[] covered = new boolean[text.length()];
    List<String> terms = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("body", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      OffsetAttribute offset = ts.addAttribute(OffsetAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        terms.add(term.toString());
        for (int i = offset.startOffset(); i < offset.endOffset(); i++) {
          covered[i] = true;
        }
      }
      ts.end();
    }
    for (int i = 0; i < text.length(); i += Character.charCount(text.codePointAt(i))) {
      int cp = text.codePointAt(i);
      if (Character.isLetterOrDigit(cp) && covered[i] == false) {
        fail(
            String.format(
                Locale.ROOT, "U+%04X of \"%s\" is in no token; tokens: %s", cp, text, terms));
      }
    }
  }

  /** Control (passes): the default mode keeps the marks, attached to the syllable. */
  public void testControlDefaultMode() throws Exception {
    assertAnalyzesTo(words, KA_SHAN, new String[] {KA_SHAN});
    assertAnalyzesTo(words, KA_KHAMTI, new String[] {KA_KHAMTI});
    assertAnalyzesTo(words, SHAN_ALONE, new String[] {SHAN_ALONE});
    assertKeepsEveryLetter(words, KAUNG_MICRO);
  }

  /** Control (passes): Burmese syllables, as in TestMyanmarSyllable#testBasics. */
  public void testControlBurmeseSyllables() throws Exception {
    assertAnalyzesTo(
        syllables,
        "သက်ဝင်လှုပ်ရှားစေပြီး",
        new String[] {"သက်", "ဝင်", "လှုပ်", "ရှား", "စေ", "ပြီး"});
  }

  public void testShanReduplication() throws Exception {
    assertKeepsEveryLetter(syllables, KA_SHAN);
    assertKeepsEveryLetter(syllables, SHAN_ALONE);
  }

  public void testKhamtiReduplication() throws Exception {
    assertKeepsEveryLetter(syllables, KA_KHAMTI);
  }

  /** The same through the factory option myanmarAsWords="false". */
  public void testFactoryOption() throws Exception {
    Map<String, String> args = new HashMap<>();
    args.put("myanmarAsWords", "false");
    ICUTokenizerFactory factory = new ICUTokenizerFactory(args);
    factory.inform(new ClasspathResourceLoader(getClass()));
    try (Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            return new TokenStreamComponents(factory.create(newAttributeFactory()));
          }
        }) {
      assertKeepsEveryLetter(a, KA_SHAN + " " + KA_KHAMTI);
    }
  }

  /**
   * Secondary: the space and MICRO SIGN are Common script, so the script iterator keeps them in the
   * Myanmar run, and the syllable rules drop the MICRO SIGN.
   */
  public void testCommonLetterAfterMyanmarText() throws Exception {
    assertKeepsEveryLetter(syllables, KAUNG_MICRO);
  }
}
