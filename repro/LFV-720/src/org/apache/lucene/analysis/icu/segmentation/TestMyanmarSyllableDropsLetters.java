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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.ClasspathResourceLoader;

/**
 * With myanmarAsWords=false, ICUTokenizer breaks Myanmar-script runs with MyanmarSyllable.rbbi,
 * whose only status rules are Myanmar consonant syllables and numbers. Every other letter of such a
 * run gets rule status 0 and is skipped like punctuation: the Myanmar modifier letters U+A9E6 / U+AA70
 * (Shan and Khamti reduplication marks), and Common-script letters that the script iterator puts into
 * the Myanmar run, such as MICRO SIGN even after a space ("ကောင်း µm" loses µ).
 */
public class TestMyanmarSyllableDropsLetters extends BaseTokenStreamTestCase {

  private static final String SHAN_REDUP = "ကꧦ"; // MYANMAR LETTER KA + U+A9E6
  private static final String KHAMTI_REDUP = "ကꩰ"; // MYANMAR LETTER KA + U+AA70
  private static final String MICRO = "ကောင်း µm"; // 'good' + space + micro sign + m

  /** Returns the letters/digits of text that lie in no token's offsets. */
  private static List<String> lost(Tokenizer t, String text) throws Exception {
    boolean[] covered = new boolean[text.length()];
    t.setReader(new StringReader(text));
    OffsetAttribute off = t.addAttribute(OffsetAttribute.class);
    t.reset();
    while (t.incrementToken()) {
      for (int i = off.startOffset(); i < off.endOffset(); i++) covered[i] = true;
    }
    t.end();
    t.close();
    List<String> out = new ArrayList<>();
    for (int i = 0; i < text.length(); ) {
      int cp = text.codePointAt(i);
      if (Character.isLetterOrDigit(cp) && covered[i] == false) {
        out.add(String.format("U+%04X in \"%s\"", cp, text));
      }
      i += Character.charCount(cp);
    }
    return out;
  }

  private static Tokenizer syllables() {
    return new ICUTokenizer(new DefaultICUTokenizerConfig(true, false));
  }

  public void testShanReduplicationMark() throws Exception {
    assertEquals(List.of(), lost(syllables(), SHAN_REDUP));
  }

  public void testKhamtiReduplicationMark() throws Exception {
    assertEquals(List.of(), lost(syllables(), KHAMTI_REDUP));
  }

  public void testMicroSignAfterMyanmarWord() throws Exception {
    assertEquals(List.of(), lost(syllables(), MICRO));
  }

  /** The same through the factory option myanmarAsWords="false". */
  public void testFactoryOption() throws Exception {
    Map<String, String> args = new HashMap<>();
    args.put("myanmarAsWords", "false");
    ICUTokenizerFactory factory = new ICUTokenizerFactory(args);
    factory.inform(new ClasspathResourceLoader(getClass()));
    assertEquals(List.of(), lost(factory.create(newAttributeFactory()), MICRO));
  }

  /** Control: the default dictionary mode keeps all of them. Passes. */
  public void testControlDefaultConfig() throws Exception {
    for (String s : new String[] {SHAN_REDUP, KHAMTI_REDUP, MICRO}) {
      assertEquals(List.of(), lost(new ICUTokenizer(), s));
    }
  }

  /** Control: Myanmar syllables themselves are kept in syllable mode. Passes. */
  public void testControlSyllables() throws Exception {
    assertEquals(List.of(), lost(syllables(), "သက်ဝင်လှုပ်ရှားစေပြီး"));
  }
}
