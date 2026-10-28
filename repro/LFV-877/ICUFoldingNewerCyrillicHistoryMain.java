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
package org.apache.lucene.analysis.icu;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.util.VersionInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Version-portable form of the ICUFoldingFilter repro, for running on every release since the
 * filter was added (Lucene 3.1). It uses only ICUFoldingFilter, TokenStream and CharTermAttribute,
 * whose signatures are the same from 3.1 to 11.0, and a one-token input stream instead of a
 * tokenizer (tokenizer constructors changed between major versions).
 *
 * <p>Exit status: 0 = every letter is folded to its base letter (bug absent), 1 (AssertionError) =
 * the bug reproduces, 2 = a control letter (older letter of the same kind) is not folded, so the
 * comparison is not meaningful on this version.
 */
public class ICUFoldingNewerCyrillicHistoryMain {

  /** Emits its text as a single token. */
  private static final class OneToken extends TokenStream {
    private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
    private final String text;
    private boolean done;

    OneToken(String text) {
      this.text = text;
    }

    @Override
    public boolean incrementToken() {
      if (done) {
        return false;
      }
      clearAttributes();
      term.setEmpty().append(text);
      done = true;
      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      done = false;
    }
  }

  static String fold(String s) throws IOException {
    TokenStream ts = new ICUFoldingFilter(new OneToken(s));
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    ts.reset();
    String out = ts.incrementToken() ? term.toString() : "";
    ts.end();
    ts.close();
    return out;
  }

  static String hex(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); ) {
      int cp = s.codePointAt(i);
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(String.format(java.util.Locale.ROOT, "U+%04X", cp));
      i += Character.charCount(cp);
    }
    return sb.length() == 0 ? "(empty)" : sb.toString();
  }

  /** {letter, base letter}: older letters of the same kind (encoded up to Unicode 3.2). */
  static final String[][] OLDER = {
    {"ҧ", "п"}, // PE WITH MIDDLE HOOK (older Abkhaz orthography) -> pe
    {"ғ", "г"}, // GHE WITH STROKE -> ghe
    {"ҕ", "г"}, // GHE WITH MIDDLE HOOK -> ghe
    {"ҳ", "х"}, // HA WITH DESCENDER -> ha
    {"ӆ", "л"}, // EL WITH TAIL -> el
    {"ң", "н"}, // EN WITH DESCENDER -> en
    {"ӈ", "н"}, // EN WITH HOOK -> en
    {"ҭ", "т"}, // TE WITH DESCENDER -> te
  };

  /** {letter, base letter, Unicode version that encoded the letter}. */
  static final String[][] NEWER = {
    {"ӷ", "г", "4.1"}, {"Ӷ", "Г", "4.1"}, // GHE WITH DESCENDER
    {"ӻ", "г", "5.0"}, {"Ӻ", "Г", "5.0"}, // GHE WITH STROKE AND HOOK
    {"ӽ", "х", "5.0"}, {"Ӽ", "Х", "5.0"}, // HA WITH HOOK
    {"ӿ", "х", "5.0"}, {"Ӿ", "Х", "5.0"}, // HA WITH STROKE
    {"ԓ", "л", "5.0"}, {"Ԓ", "Л", "5.0"}, // EL WITH HOOK
    {"ԡ", "л", "5.1"}, {"Ԡ", "Л", "5.1"}, // EL WITH MIDDLE HOOK
    {"ԣ", "н", "5.1"}, {"Ԣ", "Н", "5.1"}, // EN WITH MIDDLE HOOK
    {"ꚋ", "т", "5.1"}, {"Ꚋ", "Т", "5.1"}, // TE WITH MIDDLE HOOK
    {"ԥ", "п", "5.2"}, {"Ԥ", "П", "5.2"}, // PE WITH DESCENDER (modern Abkhaz)
    {"ԧ", "һ", "6.0"}, {"Ԧ", "Һ", "6.0"}, // SHHA WITH DESCENDER
    {"ԩ", "н", "7.0"}, {"Ԩ", "Н", "7.0"}, // EN WITH LEFT HOOK
    {"ԯ", "л", "7.0"}, {"Ԯ", "Л", "7.0"}, // EL WITH DESCENDER
  };

  public static void main(String[] args) throws Exception {
    Package corePkg = TokenStream.class.getPackage();
    System.out.println(
        "lucene-core implementation version: "
            + (corePkg == null ? null : corePkg.getImplementationVersion())
            + "; icu4j "
            + VersionInfo.ICU_VERSION
            + " (Unicode "
            + UCharacter.getUnicodeVersion()
            + ")");

    int controlFailures = 0;
    for (String[] p : OLDER) {
      String got = fold(p[0]);
      String want = fold(p[1]);
      boolean ok = got.equals(want);
      if (!ok) {
        controlFailures++;
      }
      System.out.println(
          "control " + hex(p[0]) + " -> " + hex(got) + " (base folds to " + hex(want) + ") "
              + (ok ? "folded" : "NOT FOLDED"));
    }

    List<String> unfolded = new ArrayList<String>();
    int definedHere = 0;
    int unfoldedDefinedHere = 0;
    for (String[] p : NEWER) {
      int cp = p[0].codePointAt(0);
      boolean defined = UCharacter.isDefined(cp);
      if (defined) {
        definedHere++;
      }
      String got = fold(p[0]);
      String want = fold(p[1]);
      boolean ok = got.equals(want);
      if (!ok) {
        unfolded.add(hex(p[0]));
        if (defined) {
          unfoldedDefinedHere++;
        }
      }
      System.out.println(
          "newer "
              + hex(p[0])
              + " (Unicode "
              + p[2]
              + ", "
              + (defined ? "assigned" : "unassigned")
              + " in this icu4j) -> "
              + hex(got)
              + " (base folds to "
              + hex(want)
              + ") "
              + (ok ? "folded" : "NOT FOLDED"));
    }

    String older = "аҧсуа"; // Abkhaz, older orthography (U+04A7)
    String modern = "аԥсуа"; // Abkhaz, modern orthography (U+0525)
    String olderTerm = fold(older);
    String modernTerm = fold(modern);
    boolean sameTerm = olderTerm.equals(modernTerm);
    System.out.println(
        "abkhaz word: older spelling -> "
            + hex(olderTerm)
            + "; modern spelling -> "
            + hex(modernTerm)
            + (sameTerm ? " (same term)" : " (DIFFERENT terms)"));

    if (controlFailures > 0) {
      System.out.println("RESULT: CONTROL_FAIL " + controlFailures + " of " + OLDER.length);
      System.exit(2);
    }
    String summary =
        unfolded.size()
            + " of "
            + NEWER.length
            + " letters keep their hook/stroke/descender ("
            + unfoldedDefinedHere
            + " of the "
            + definedHere
            + " assigned in this icu4j); abkhaz spellings "
            + (sameTerm ? "match" : "differ")
            + ": "
            + unfolded;
    if (!unfolded.isEmpty() || !sameTerm) {
      System.out.println("RESULT: BUG " + summary);
      throw new AssertionError(summary);
    }
    System.out.println("RESULT: OK " + summary);
  }
}
