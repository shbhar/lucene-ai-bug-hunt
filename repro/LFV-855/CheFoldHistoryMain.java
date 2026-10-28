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
package utr30hist;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.icu.ICUFoldingFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Version-independent history check (Lucene 3.1 .. main): only APIs that exist unchanged in every
 * release since ICUFoldingFilter was added (TokenStream, CharTermAttribute,
 * ICUFoldingFilter(TokenStream)).
 *
 * <p>ICUFoldingFilter documents "Diacritic removal (including stroke, hook, descender)". CHE WITH
 * DESCENDER (U+04B7, U+04B6) must fold to CHE (U+0447), as the other Cyrillic letters WITH
 * DESCENDER fold to their base letter. Exits non-zero (AssertionError) when the bug reproduces.
 */
public final class CheFoldHistoryMain {

  /** Emits exactly one token. Final, as TokenStream's assertion requires. */
  private static final class OneToken extends TokenStream {
    private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
    private final String text;
    private boolean done;

    OneToken(String text) {
      this.text = text;
    }

    @Override
    public final boolean incrementToken() throws IOException {
      if (done) {
        return false;
      }
      clearAttributes();
      char[] chars = text.toCharArray();
      term.copyBuffer(chars, 0, chars.length);
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
    String out = ts.incrementToken() ? term.toString() : null;
    ts.end();
    ts.close();
    return out;
  }

  static String cps(String s) {
    if (s == null) {
      return "null";
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); ) {
      int cp = s.codePointAt(i);
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(String.format("U+%04X", cp));
      i += Character.charCount(cp);
    }
    return sb.toString();
  }

  static String hex(byte[] b) {
    StringBuilder sb = new StringBuilder();
    for (byte x : b) {
      sb.append(String.format("%02x", x & 0xff));
    }
    return sb.toString();
  }

  public static void main(String[] args) throws Exception {
    // provenance: Lucene and ICU4J versions, utr30.nrm checksums (sha1 and git blob id)
    String luceneVersion = TokenStream.class.getPackage().getImplementationVersion();
    String icuVersion = String.valueOf(com.ibm.icu.util.VersionInfo.ICU_VERSION);
    byte[] nrm;
    try (InputStream in = ICUFoldingFilter.class.getResourceAsStream("utr30.nrm")) {
      nrm = in.readAllBytes();
    }
    MessageDigest md = MessageDigest.getInstance("SHA-1");
    String nrmSha1 = hex(md.digest(nrm));
    md.reset();
    md.update(("blob " + nrm.length + "\0").getBytes("US-ASCII"));
    md.update(nrm);
    String nrmBlob = hex(md.digest());
    System.out.println(
        "PROVENANCE lucene="
            + luceneVersion
            + " icu4j="
            + icuVersion
            + " utr30.nrm bytes="
            + nrm.length
            + " sha1="
            + nrmSha1
            + " gitblob="
            + nrmBlob);

    // controls: the other Cyrillic letters WITH DESCENDER / WITH VERTICAL STROKE lose the mark
    String[][] controls = {
      {"җ", "ж"}, // zhe with descender -> zhe
      {"Җ", "ж"}, // capital
      {"қ", "к"}, // ka with descender -> ka
      {"ң", "н"}, // en with descender -> en
      {"ҳ", "х"}, // ha with descender -> ha
      {"ҹ", "ч"}, // che with vertical stroke -> che
      {"Ҹ", "ч"}, // capital
      {"ҿ", "ҽ"}, // abkhasian che with descender -> abkhasian che
    };
    boolean controlsOk = true;
    for (String[] c : controls) {
      String got = fold(c[0]);
      boolean ok = c[1].equals(got);
      controlsOk &= ok;
      System.out.println(
          "CONTROL " + cps(c[0]) + " -> " + cps(got) + (ok ? " ok" : " UNEXPECTED (want " + cps(c[1]) + ")"));
    }

    String small = fold("ҷ");
    String capital = fold("Ҷ");
    String khakassian = fold("ӌ"); // recorded only (secondary manifestation, not asserted)
    String word = fold("Тоҷикистон"); // To(che w/ desc)ikiston
    String plain = fold("Точикистон"); // Tochikiston
    System.out.println("CASE U+04B7 -> " + cps(small));
    System.out.println("CASE U+04B6 -> " + cps(capital));
    System.out.println("INFO U+04CC -> " + cps(khakassian));
    System.out.println("WORD tajik=" + cps(word));
    System.out.println("WORD plain=" + cps(plain) + " sameTerm=" + plain.equals(word));

    boolean affected = !"ч".equals(small) || !"ч".equals(capital);
    System.out.println(
        "RESULT controls="
            + (controlsOk ? "ok" : "FAILED")
            + " U+04B7="
            + cps(small)
            + " U+04B6="
            + cps(capital)
            + " affected="
            + affected);
    if (!controlsOk) {
      throw new IllegalStateException("control failed: harness not valid on this version");
    }
    if (affected) {
      throw new AssertionError(
          "CHE WITH DESCENDER folds to " + cps(small) + "/" + cps(capital) + ", expected U+0447 (che)");
    }
  }
}
