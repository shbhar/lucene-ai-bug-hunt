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
package org.apache.lucene.analysis.hunspell;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.CharsRef;

/**
 * "FLAG UTF-8" means the flags are UTF-8 encoded characters (hunspell(5)). The rest of the .aff
 * and .dic files is pure ASCII here, so the file bytes are identical whatever the SET line says,
 * and the dictionary must mean the same thing for every SET value.
 *
 * <p>Two suffix classes use two different non-ASCII flags whose UTF-8 encodings share the lead
 * byte. "walk" carries only the "-s" flag and "jump" carries only the "-ed" flag, so "walked" and
 * "jumps" must be rejected. Uses only public API (Dictionary, Hunspell, Stemmer).
 */
public class TestFlagUtf8Under8BitSet extends LuceneTestCase {

  // U+00E9 (C3 A9) and U+00F6 (C3 B6): same UTF-8 lead byte 0xC3
  private static final String E_ACUTE = "é";
  private static final String O_UMLAUT = "ö";
  // U+00E6 (C3 A6) and U+00E8 (C3 A8): continuation bytes that ISO8859-14 maps outside Latin-1
  private static final String AE = "æ";
  private static final String E_GRAVE = "è";
  // U+0410 (D0 90) and U+0411 (D0 91): Cyrillic capital A and BE
  private static final String CYR_A = "А";
  private static final String CYR_BE = "Б";

  private static Dictionary load(String set, String flagS, String flagEd) throws Exception {
    StringBuilder aff = new StringBuilder();
    if (set != null) {
      aff.append("SET ").append(set).append('\n');
    }
    aff.append("FLAG UTF-8\n");
    aff.append("SFX ").append(flagS).append(" Y 1\n");
    aff.append("SFX ").append(flagS).append(" 0 s .\n");
    aff.append("SFX ").append(flagEd).append(" Y 1\n");
    aff.append("SFX ").append(flagEd).append(" 0 ed .\n");
    String dic = "2\nwalk/" + flagS + "\njump/" + flagEd + "\n";
    // Flags written as UTF-8 bytes (as FLAG UTF-8 requires); everything else is ASCII.
    try (Directory dir = new ByteBuffersDirectory()) {
      return new Dictionary(
          dir,
          "dictionary",
          new ByteArrayInputStream(aff.toString().getBytes(UTF_8)),
          new ByteArrayInputStream(dic.getBytes(UTF_8)));
    }
  }

  private static void check(String set, String flagS, String flagEd) throws Exception {
    Dictionary d = load(set, flagS, flagEd);
    Hunspell h = new Hunspell(d);
    String ctx = "SET " + set + ", FLAG UTF-8, flags U+" + hex(flagS) + "/U+" + hex(flagEd) + ": ";
    assertTrue(ctx + "walk", h.spell("walk"));
    assertTrue(ctx + "walks", h.spell("walks"));
    assertTrue(ctx + "jump", h.spell("jump"));
    assertTrue(ctx + "jumped", h.spell("jumped"));
    assertFalse(ctx + "walked must be rejected (walk has only the -s flag)", h.spell("walked"));
    assertFalse(ctx + "jumps must be rejected (jump has only the -ed flag)", h.spell("jumps"));
    List<CharsRef> stems = new Stemmer(d).stem("walked");
    assertTrue(ctx + "stems of walked should be empty, got " + stems, stems.isEmpty());
  }

  private static String hex(String s) {
    return Integer.toHexString(s.charAt(0)).toUpperCase(java.util.Locale.ROOT);
  }

  // ---- controls: expected to pass ----
  public void testControlNoSet() throws Exception {
    check(null, E_ACUTE, O_UMLAUT);
  }

  public void testControlIso8859_1() throws Exception {
    check("ISO8859-1", E_ACUTE, O_UMLAUT);
  }

  public void testControlUtf8() throws Exception {
    check("UTF-8", E_ACUTE, O_UMLAUT);
  }

  // ---- 8-bit SET values other than ISO8859-1 ----
  public void testIso8859_15() throws Exception {
    check("ISO8859-15", E_ACUTE, O_UMLAUT);
  }

  public void testIso8859_5() throws Exception {
    check("ISO8859-5", E_ACUTE, O_UMLAUT);
  }

  public void testKoi8rCyrillicFlags() throws Exception {
    check("KOI8-R", CYR_A, CYR_BE);
  }

  public void testCp1251CyrillicFlags() throws Exception {
    check("microsoft-cp1251", CYR_A, CYR_BE);
  }

  public void testIso8859_14() throws Exception {
    check("ISO8859-14", AE, E_GRAVE);
  }

  // ISO8859-14 path with continuation bytes that the ISO8859-14 table maps to Latin-1 itself:
  // expected to work by accident (shows the ISO8859-14 failure depends on the flag bytes).
  public void testIso8859_14LatinCompatibleBytes() throws Exception {
    check("ISO8859-14", E_ACUTE, O_UMLAUT);
  }

  // Stemmer alone (the spell checks above fail first): "jumps" must not stem to "jump".
  public void testStemmerKoi8rCyrillicFlags() throws Exception {
    Dictionary d = load("KOI8-R", CYR_A, CYR_BE);
    assertEquals(List.of("walk"), new Stemmer(d).stem("walks").stream().map(CharsRef::toString).toList());
    List<CharsRef> stems = new Stemmer(d).stem("jumps");
    assertTrue("SET KOI8-R, FLAG UTF-8: stems of jumps should be empty, got " + stems, stems.isEmpty());
  }

  // ISO8859-3 has no character at 0xC3, the UTF-8 lead byte of both flags.
  public void testIso8859_3Loads() throws Exception {
    check("ISO8859-3", E_ACUTE, O_UMLAUT);
  }
}
