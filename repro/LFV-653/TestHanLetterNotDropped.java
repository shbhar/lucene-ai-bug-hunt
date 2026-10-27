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
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.text.UnicodeSetIterator;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.cjk.CJKBigramFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * Independent check: with {@code new DefaultICUTokenizerConfig(false, true)} ("otherwise text will
 * be segmented according to UAX#29 defaults"), a character whose UAX #29 Word_Break value is ALetter
 * must not disappear from the token stream. U+3005 IDEOGRAPHIC ITERATION MARK is Word_Break=ALetter
 * and Script=Han, but not Ideographic.
 *
 * <p>Only public API is used.
 */
public class TestHanLetterNotDropped extends BaseTokenStreamTestCase {

  private static final class Tok {
    final String text;
    final int start, end;

    Tok(String text, int start, int end) {
      this.text = text;
      this.start = start;
      this.end = end;
    }

    @Override
    public String toString() {
      return esc(text) + "[" + start + "," + end + ")";
    }
  }

  private static String esc(String s) {
    StringBuilder sb = new StringBuilder();
    s.codePoints()
        .forEach(
            cp -> sb.append(cp < 0x80 ? String.valueOf((char) cp) : String.format("\\u{%04X}", cp)));
    return sb.toString();
  }

  private static List<Tok> tokens(TokenStream ts, String text) throws IOException {
    List<Tok> out = new ArrayList<>();
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    OffsetAttribute off = ts.addAttribute(OffsetAttribute.class);
    try {
      ts.reset();
      while (ts.incrementToken()) {
        out.add(new Tok(term.toString(), off.startOffset(), off.endOffset()));
      }
      ts.end();
    } finally {
      ts.close();
    }
    return out;
  }

  private Tokenizer icu(boolean cjkAsWords, String text) {
    Tokenizer t =
        new ICUTokenizer(newAttributeFactory(), new DefaultICUTokenizerConfig(cjkAsWords, true));
    t.setReader(new StringReader(text));
    return t;
  }

  private static Tokenizer standard(String text) {
    Tokenizer t = new StandardTokenizer();
    t.setReader(new StringReader(text));
    return t;
  }

  /**
   * Oracle: every input code point with Word_Break=ALetter must lie inside some token's offset span
   * and its text must appear in that token.
   */
  private static void assertAletterCovered(String what, String text, List<Tok> toks) {
    for (int i = 0; i < text.length(); ) {
      int cp = text.codePointAt(i);
      int len = Character.charCount(cp);
      if (UCharacter.getIntPropertyValue(cp, UProperty.WORD_BREAK) == UCharacter.WordBreak.ALETTER) {
        boolean covered = false;
        String c = new String(Character.toChars(cp));
        for (Tok t : toks) {
          if (t.start <= i && i + len <= t.end && t.text.contains(c)) {
            covered = true;
            break;
          }
        }
        assertTrue(
            what
                + ": Word_Break=ALetter character "
                + String.format("U+%04X", cp)
                + " at offset "
                + i
                + " of \""
                + esc(text)
                + "\" is in no token; tokens="
                + toks,
            covered);
      }
      i += len;
    }
  }

  // ---- bug assertions (expected to FAIL on the affected versions) ----

  /** 人々 ("people"): UAX #29 gives 人 | 々 (WB999 break between Other and ALetter). */
  public void testIterationMarkKeptCjkAsWordsFalse() throws Exception {
    String text = "人々";
    assertAletterCovered("ICUTokenizer(cjkAsWords=false)", text, tokens(icu(false, text), text));
  }

  /** 様々な人々 ("various people"). */
  public void testIterationMarkKeptInSentenceCjkAsWordsFalse() throws Exception {
    String text = "様々な人々";
    assertAletterCovered("ICUTokenizer(cjkAsWords=false)", text, tokens(icu(false, text), text));
  }

  /**
   * The module's own documented pipeline (TestWithCJKBigramFilter): ICUTokenizer(cjkAsWords=false)
   * followed by CJKBigramFilter. The iteration mark should end up in some term.
   */
  public void testIterationMarkKeptThroughCjkBigramFilter() throws Exception {
    String text = "人々";
    List<Tok> toks = tokens(new CJKBigramFilter(icu(false, text)), text);
    assertAletterCovered("ICUTokenizer(cjkAsWords=false)+CJKBigramFilter", text, toks);
  }

  /** Every Han-script Word_Break=ALetter code point after an ideograph, per the running ICU data. */
  public void testAllHanAletterCodePointsCjkAsWordsFalse() throws Exception {
    UnicodeSet set = new UnicodeSet("[[:Script=Han:]&[:Word_Break=ALetter:]]");
    assertTrue("expected a non-empty set", set.size() > 0);
    List<String> dropped = new ArrayList<>();
    for (UnicodeSetIterator it = new UnicodeSetIterator(set); it.next(); ) {
      String text = "人" + new String(Character.toChars(it.codepoint));
      try {
        assertAletterCovered("ICUTokenizer(cjkAsWords=false)", text, tokens(icu(false, text), text));
      } catch (AssertionError e) {
        dropped.add(String.format("U+%04X", it.codepoint));
      }
    }
    assertTrue(
        "Han-script Word_Break=ALetter code points dropped by ICUTokenizer(cjkAsWords=false): "
            + dropped
            + " of "
            + set.size(),
        dropped.isEmpty());
  }

  // ---- controls (expected to PASS everywhere) ----

  /** Reference UAX #29 implementation in core keeps the mark: 人 | 々. */
  public void testControlStandardTokenizerKeepsIt() throws Exception {
    String text = "人々";
    List<Tok> toks = tokens(standard(text), text);
    assertAletterCovered("StandardTokenizer", text, toks);
    assertEquals(2, toks.size());
    assertEquals("人", toks.get(0).text);
    assertEquals("々", toks.get(1).text);
  }

  /** Default config (dictionary segmentation) keeps 人々 as one word. */
  public void testControlCjkAsWordsTrueKeepsIt() throws Exception {
    String text = "人々";
    List<Tok> toks = tokens(icu(true, text), text);
    assertAletterCovered("ICUTokenizer(cjkAsWords=true)", text, toks);
  }

  /** The oracle itself works on ordinary letters and the same config. */
  public void testControlLatinAndKanaUnaffected() throws Exception {
    String text = "abc ひらがな 人 def";
    assertAletterCovered("ICUTokenizer(cjkAsWords=false)", text, tokens(icu(false, text), text));
  }
}
