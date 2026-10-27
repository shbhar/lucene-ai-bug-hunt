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
package org.apache.lucene.analysis.wikipedia;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.classic.ClassicTokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * WikipediaTokenizer is kept on the Unicode 3.0 behaviour of ClassicTokenizer, which it clones
 * (LUCENE-6993), but its LETTER and DIGIT classes are fixed lists of code point ranges
 * (WikipediaTokenizerImpl.jflex LETTER/DIGIT macros), not the Unicode 3.0 letter and digit classes
 * that ClassicTokenizer uses. So letters and digits that Unicode 3.0 already had are silently dropped
 * from plain text: MICRO SIGN in "5 µm", the ordinals in "1ª" and "2º", fullwidth digits, Latin
 * ligatures, Yi syllables. ClassicTokenizer and StandardTokenizer keep all of them.
 */
public class TestWikipediaTokenizerLetterRanges extends BaseTokenStreamTestCase {

  /** Every letter and digit here is in Unicode 3.0 (1999) or earlier. */
  private static final String[] TEXTS = {
    "5 µm", // U+00B5 MICRO SIGN (Ll, Unicode 1.1)
    "1ª edición 2º piso", // U+00AA / U+00BA ordinal indicators (Lo, Unicode 1.1)
    "２０２６ year", // fullwidth digits U+FF12 U+FF10 U+FF16 (Nd, Unicode 1.1)
    "ﬁle", // U+FB01 LATIN SMALL LIGATURE FI (Ll, Unicode 1.1)
    "ꀀꀁ", // U+A000 U+A001 YI SYLLABLES (Lo, Unicode 3.0)
  };

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

  private static List<String> lostAll(Supplier<Tokenizer> f) throws Exception {
    List<String> all = new ArrayList<>();
    for (String text : TEXTS) all.addAll(lost(f.get(), text));
    return all;
  }

  public void testWikipediaTokenizerKeepsLetters() throws Exception {
    List<String> l = lostAll(WikipediaTokenizer::new);
    assertTrue("WikipediaTokenizer dropped " + l, l.isEmpty());
  }

  /** Control: StandardTokenizer keeps every letter and digit of the same texts. Passes. */
  public void testControlStandardTokenizer() throws Exception {
    assertEquals(List.of(), lostAll(StandardTokenizer::new));
  }

  /** Control: ClassicTokenizer (the grammar WikipediaTokenizer clones) keeps all of them. Passes. */
  public void testControlClassicTokenizer() throws Exception {
    assertEquals(List.of(), lostAll(ClassicTokenizer::new));
  }
}
