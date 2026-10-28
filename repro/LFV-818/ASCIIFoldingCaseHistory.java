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
package repro;

import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Version-portable repro for ASCIIFoldingFilter's opposite-case table entries (Lucene 2.9.0 to main).
 *
 * <p>It runs WhitespaceTokenizer + ASCIIFoldingFilter (and WhitespaceTokenizer + LowerCaseFilter +
 * ASCIIFoldingFilter) through the public TokenStream API. The classes are found by reflection only
 * because their packages and constructors moved between releases (core to analysis-common, the
 * Version argument of 3.1 to 4.10, TermAttribute to CharTermAttribute). It uses JDK classes only, so
 * the same source compiles against every release.
 *
 * <p>Output: one line per letter (expected vs actual fold), the lowercase-chain tokens, and a SHA-1 of
 * the fold of every BMP code unit from U+0080 to U+FFFF (surrogates and whitespace excluded), so runs on
 * different releases can be compared. Exit status 1 (AssertionError) means the bug reproduces: at least
 * one of the six clear entries folds to the ASCII letter of the opposite case.
 */
public class ASCIIFoldingCaseHistory {

  /** {code point, expected fold, label}. The six clear entries. */
  static final Object[][] CLEAR = {
    {0x01E5, "g", "LATIN SMALL LETTER G WITH STROKE"},
    {0x01E7, "g", "LATIN SMALL LETTER G WITH CARON"},
    {0xA785, "s", "LATIN SMALL LETTER INSULAR S"},
    {0xA784, "S", "LATIN CAPITAL LETTER INSULAR S"},
    {0x2C6F, "A", "LATIN CAPITAL LETTER TURNED A"},
    {0xA73E, "C", "LATIN CAPITAL LETTER REVERSED C WITH DOT"},
  };

  /** Reported, not asserted: general category Ll, but no case partner and a tall C glyph. */
  static final Object[][] CONTESTED = {
    {0x0297, "c", "LATIN LETTER STRETCHED C"},
  };

  /** Controls: the same-case partners and neighbours that fold correctly. */
  static final Object[][] CONTROLS = {
    {0x01E4, "G", "LATIN CAPITAL LETTER G WITH STROKE"},
    {0x01E6, "G", "LATIN CAPITAL LETTER G WITH CARON"},
    {0x011F, "g", "LATIN SMALL LETTER G WITH BREVE"},
    {0xA782, "R", "LATIN CAPITAL LETTER INSULAR R"},
    {0xA783, "r", "LATIN SMALL LETTER INSULAR R"},
    {0x0250, "a", "LATIN SMALL LETTER TURNED A"},
    {0xA73F, "c", "LATIN SMALL LETTER REVERSED C WITH DOT"},
    {0x00C0, "A", "LATIN CAPITAL LETTER A WITH GRAVE"},
    {0x00E0, "a", "LATIN SMALL LETTER A WITH GRAVE"},
  };

  static Class<?> load(String... names) {
    for (String n : names) {
      try {
        return Class.forName(n);
      } catch (ClassNotFoundException e) {
        // try the next name
      }
    }
    return null;
  }

  static Object currentVersion() throws Exception {
    Class<?> ver = load("org.apache.lucene.util.Version");
    if (ver == null) {
      return null;
    }
    for (String f : new String[] {"LATEST", "LUCENE_CURRENT"}) {
      try {
        return ver.getField(f).get(null);
      } catch (NoSuchFieldException e) {
        // try the next name
      }
    }
    return null;
  }

  static Object newWhitespaceTokenizer(Reader r) throws Exception {
    Class<?> wt =
        load(
            "org.apache.lucene.analysis.core.WhitespaceTokenizer",
            "org.apache.lucene.analysis.WhitespaceTokenizer");
    try {
      Object t = wt.getConstructor().newInstance(); // 5.0 and later
      wt.getMethod("setReader", Reader.class).invoke(t, r);
      return t;
    } catch (NoSuchMethodException e) {
      // older constructor forms
    }
    Object v = currentVersion();
    if (v != null) {
      try {
        return wt.getConstructor(v.getClass(), Reader.class).newInstance(v, r); // 3.1 to 4.10
      } catch (NoSuchMethodException e) {
        // 2.9 and 3.0
      }
    }
    return wt.getConstructor(Reader.class).newInstance(r);
  }

  static Object wrap(Class<?> filterClass, Object in) throws Exception {
    Class<?> ts = Class.forName("org.apache.lucene.analysis.TokenStream");
    try {
      return filterClass.getConstructor(ts).newInstance(in);
    } catch (NoSuchMethodException e) {
      Object v = currentVersion(); // 4.x LowerCaseFilter(Version, TokenStream)
      return filterClass.getConstructor(v.getClass(), ts).newInstance(v, in);
    }
  }

  static Class<?> foldingClass() {
    return load(
        "org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter",
        "org.apache.lucene.analysis.ASCIIFoldingFilter");
  }

  /** Tokens of text through WhitespaceTokenizer [+ LowerCaseFilter] + ASCIIFoldingFilter. */
  static List<String> analyze(String text, boolean lowercaseFirst) throws Exception {
    Object stream = newWhitespaceTokenizer(new StringReader(text));
    if (lowercaseFirst) {
      Class<?> lc =
          load(
              "org.apache.lucene.analysis.LowerCaseFilter",
              "org.apache.lucene.analysis.core.LowerCaseFilter");
      stream = wrap(lc, stream);
    }
    stream = wrap(foldingClass(), stream);
    Class<?> charTerm = load("org.apache.lucene.analysis.tokenattributes.CharTermAttribute");
    Class<?> termAtt = load("org.apache.lucene.analysis.tokenattributes.TermAttribute");
    Class<?> attClass = charTerm != null ? charTerm : termAtt;
    Object att = stream.getClass().getMethod("addAttribute", Class.class).invoke(stream, attClass);
    Method term = charTerm != null ? null : termAtt.getMethod("term");
    Class<?> ts = Class.forName("org.apache.lucene.analysis.TokenStream");
    Method incrementToken = ts.getMethod("incrementToken");
    List<String> out = new ArrayList<>();
    ts.getMethod("reset").invoke(stream);
    while ((Boolean) incrementToken.invoke(stream)) {
      out.add(term == null ? att.toString() : (String) term.invoke(att));
    }
    ts.getMethod("end").invoke(stream);
    ts.getMethod("close").invoke(stream);
    return out;
  }

  static String fold(int cp) throws Exception {
    List<String> t = analyze(new String(Character.toChars(cp)), false);
    return t.size() == 1 ? t.get(0) : String.valueOf(t);
  }

  static int report(String kind, Object[][] rows) throws Exception {
    int wrong = 0;
    for (Object[] r : rows) {
      int cp = (Integer) r[0];
      String got = fold(cp);
      boolean ok = got.equals(r[1]);
      if (!ok) {
        wrong++;
      }
      System.out.printf(
          "%-9s U+%04X %s [%s]: expected '%s', got '%s' %s%n",
          kind, cp, new String(Character.toChars(cp)), r[2], r[1], got, ok ? "ok" : "WRONG CASE");
    }
    return wrong;
  }

  public static void main(String[] args) throws Exception {
    Class<?> f = foldingClass();
    System.out.println("ASCIIFoldingFilter class: " + f.getName());
    Package p = f.getPackage();
    System.out.println(
        "Lucene jar implementation version: " + (p == null ? "?" : p.getImplementationVersion()));

    int clearWrong = report("clear", CLEAR);
    int contestedWrong = report("contested", CONTESTED);
    int controlWrong = report("control", CONTROLS);

    String text = "naǧib NAǦIB aǥa šiõǥǥ ꞅet Ꞅet ağa";
    System.out.println("lowercase chain: '" + text + "' -> " + analyze(text, true));
    System.out.println("expected       : [nagib, nagib, aga, siogg, set, set, aga]");

    // Fold of every BMP code unit U+0080..U+FFFF (no surrogates, no whitespace), one token each.
    StringBuilder all = new StringBuilder();
    List<Integer> cps = new ArrayList<>();
    for (int c = 0x80; c <= 0xFFFF; c++) {
      if (Character.isSurrogate((char) c) || Character.isWhitespace(c)) {
        continue;
      }
      cps.add(c);
      all.append((char) c).append(' ');
    }
    List<String> folded = analyze(all.toString(), false);
    if (folded.size() != cps.size()) {
      throw new IllegalStateException("token count " + folded.size() + " != " + cps.size());
    }
    MessageDigest md = MessageDigest.getInstance("SHA-1");
    int changed = 0;
    for (int i = 0; i < cps.size(); i++) {
      String in = new String(Character.toChars(cps.get(i)));
      if (!folded.get(i).equals(in)) {
        changed++;
      }
      md.update(String.format("%04X:%s%n", cps.get(i), folded.get(i)).getBytes("UTF-8"));
    }
    StringBuilder hex = new StringBuilder();
    for (byte b : md.digest()) {
      hex.append(String.format("%02x", b));
    }
    System.out.println(
        "BMP fold: " + cps.size() + " code units, " + changed + " folded, sha1=" + hex);

    System.out.printf(
        "summary: clear entries wrong %d/%d, contested wrong %d/%d, controls wrong %d/%d%n",
        clearWrong, CLEAR.length, contestedWrong, CONTESTED.length, controlWrong, CONTROLS.length);
    if (controlWrong != 0) {
      throw new IllegalStateException("control mismatch: the harness, not the table, is wrong");
    }
    if (clearWrong != 0) {
      throw new AssertionError(
          clearWrong + " of " + CLEAR.length + " letters fold to the ASCII letter of the opposite case");
    }
    System.out.println("no opposite-case folds");
  }
}
