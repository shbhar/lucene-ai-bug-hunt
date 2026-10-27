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
package org.apache.lucene.analysis.ckb;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.BytesRef;

/**
 * Version-independent repro for the Sorani word-final HEH rule (main() mode, public API only, no
 * test framework, so the same source compiles against every release that has the ckb package,
 * 4.7.0 and later).
 *
 * <p>SoraniNormalizer documents that a word-final HEH (U+0647) becomes AE (U+06D5) and that
 * "formatting characters such as directional controls are removed". A word ending in HEH followed
 * by an invisible directional mark (RLM U+200F, LRM U+200E, ALM U+061C) keeps HEH, while the same
 * word without the mark gets AE.
 *
 * <p>Exit status: 0 = documented behaviour, 1 = bug reproduced, 3 = a control failed (the harness
 * itself is not trustworthy on this version).
 */
public class SoraniFinalHehAcrossVersions {

  /** Emits exactly one token with the given text, so the filter is tested alone. */
  static final class OneToken extends TokenStream {
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

  // KHAH ALEF NOON HEH ("house", final vowel typed with HEH) and its documented normal form
  static final String WORD = "\u062E\u0627\u0646\u0647";
  static final String EXPECTED = "\u062E\u0627\u0646\u06D5";
  // KHAH ALEF NOON (no final HEH): a trailing mark must simply be removed
  static final String NO_HEH = "\u062E\u0627\u0646";

  static String hex(String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      if (i > 0) b.append(' ');
      b.append(String.format("U+%04X", (int) s.charAt(i)));
    }
    return b.toString();
  }

  static String filter(String in) throws IOException {
    TokenStream ts = new SoraniNormalizationFilter(new OneToken(in));
    try {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      if (!ts.incrementToken()) throw new IllegalStateException("no token");
      String s = term.toString();
      if (ts.incrementToken()) throw new IllegalStateException("more than one token");
      ts.end();
      return s;
    } finally {
      ts.close();
    }
  }

  static List<String> analyze(Analyzer a, String text) throws IOException {
    List<String> out = new ArrayList<String>();
    TokenStream ts = a.tokenStream("f", new StringReader(text));
    try {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) out.add(hex(term.toString()));
      ts.end();
    } finally {
      ts.close();
    }
    return out;
  }

  /** SoraniAnalyzer() since 5.0; SoraniAnalyzer(Version) in 4.x. Public constructors only. */
  static Analyzer newSoraniAnalyzer() throws Exception {
    try {
      return SoraniAnalyzer.class.getConstructor().newInstance();
    } catch (NoSuchMethodException e) {
      Class<?> v = Class.forName("org.apache.lucene.util.Version");
      Object ver;
      try {
        ver = v.getField("LATEST").get(null);
      } catch (NoSuchFieldException e2) {
        ver = v.getField("LUCENE_CURRENT").get(null);
      }
      return SoraniAnalyzer.class.getConstructor(v).newInstance(ver);
    }
  }

  static int bugs = 0;
  static int controlFailures = 0;

  static void check(boolean control, String name, String expected, String actual) {
    boolean ok = expected.equals(actual);
    if (!ok) {
      if (control) controlFailures++;
      else bugs++;
    }
    System.out.println(
        (ok ? "PASS " : (control ? "CONTROL-FAIL " : "BUG ")) + name + ": expected=[" + expected
            + "] actual=[" + actual + "]");
  }

  public static void main(String[] args) throws Exception {
    System.out.println(
        "java.version="
            + System.getProperty("java.version")
            + " Character.getType(U+061C)==FORMAT: "
            + (Character.getType('\u061C') == Character.FORMAT));

    // Controls: the documented rules work when nothing follows the HEH / when there is no HEH.
    check(true, "filter(word)", hex(EXPECTED), hex(filter(WORD)));
    check(true, "filter(noHeh+RLM)", hex(NO_HEH), hex(filter(NO_HEH + "\u200F")));

    // The bug: a trailing directional mark is removed, but the HEH before it is not converted.
    check(false, "filter(word+RLM)", hex(EXPECTED), hex(filter(WORD + "\u200F")));
    check(false, "filter(word+LRM)", hex(EXPECTED), hex(filter(WORD + "\u200E")));
    check(false, "filter(word+ALM)", hex(EXPECTED), hex(filter(WORD + "\u061C")));

    Analyzer a = newSoraniAnalyzer();
    try {
      // Index-time chain (tokenizer + normalization + stemming): marked and unmarked word differ.
      check(
          false,
          "SoraniAnalyzer.tokenStream(word+RLM) vs (word)",
          analyze(a, WORD).toString(),
          analyze(a, WORD + "\u200F").toString());
      // Query-time Analyzer.normalize (added in 6.2.0); absent in older releases.
      Method normalize = null;
      try {
        normalize = Analyzer.class.getMethod("normalize", String.class, String.class);
      } catch (NoSuchMethodException e) {
        System.out.println("SKIP SoraniAnalyzer.normalize: Analyzer.normalize(String,String) absent");
      }
      if (normalize != null) {
        String plain = ((BytesRef) normalize.invoke(a, "f", WORD)).utf8ToString();
        String marked = ((BytesRef) normalize.invoke(a, "f", WORD + "\u200F")).utf8ToString();
        check(false, "SoraniAnalyzer.normalize(word+RLM) vs (word)", hex(plain), hex(marked));
      }
    } finally {
      a.close();
    }

    System.out.println("RESULT bugs=" + bugs + " controlFailures=" + controlFailures);
    if (controlFailures > 0) System.exit(3);
    if (bugs > 0) System.exit(1);
  }
}
