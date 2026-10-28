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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.CharsRef;

/**
 * hunspell(5): "FLAG value ... `UTF-8' parameter sets UTF-8 encoded Unicode character flags." C Hunspell decodes such
 * flags from the raw bytes as UTF-8 whatever the SET encoding is (hashmgr.cxx decode_flags/decode_flag, FLAG_UNI).
 *
 * <p>Dictionary used by every test: two suffix classes whose flags are the UTF-8 characters U+00E1 (bytes C3 A1) and
 * U+00E2 (bytes C3 A2); "kot" may take suffix "a" (flag U+00E1), "las" may take suffix "e" (flag U+00E2). The words
 * are ASCII, so they read the same in every 8-bit SET. C Hunspell 1.3.2 and 1.7.2 accept kot, kota, las, lase and
 * reject kote, lasa for every SET value tested. Public API (Dictionary, Hunspell) plus the package-private Stemmer.
 */
public class TestFlagUtf8With8BitSet extends LuceneTestCase {

  private static Dictionary load(String set) throws Exception {
    ByteArrayOutputStream aff = new ByteArrayOutputStream();
    if (set != null) aff.write(("SET " + set + "\n").getBytes(StandardCharsets.US_ASCII));
    aff.write("FLAG UTF-8\n".getBytes(StandardCharsets.US_ASCII));
    aff.write("SFX á Y 1\nSFX á 0 a .\n".getBytes(StandardCharsets.UTF_8));
    aff.write("SFX â Y 1\nSFX â 0 e .\n".getBytes(StandardCharsets.UTF_8));
    byte[] dic = "2\nkot/á\nlas/â\n".getBytes(StandardCharsets.UTF_8);
    return new Dictionary(
        new ByteBuffersDirectory(),
        "flagutf8",
        new ByteArrayInputStream(aff.toByteArray()),
        new ByteArrayInputStream(dic));
  }

  private static void assertVerdicts(String set) throws Exception {
    Hunspell h = new Hunspell(load(set));
    String ctx = "SET " + set + ", FLAG UTF-8: ";
    assertTrue(ctx + "kot", h.spell("kot"));
    assertTrue(ctx + "kota (kot + suffix of flag U+00E1)", h.spell("kota"));
    assertTrue(ctx + "lase (las + suffix of flag U+00E2)", h.spell("lase"));
    assertFalse(ctx + "kote must be rejected: kot has only flag U+00E1", h.spell("kote"));
    assertFalse(ctx + "lasa must be rejected: las has only flag U+00E2", h.spell("lasa"));
  }

  /** Control: ISO-8859-1 (the only 8-bit SET that LUCENE-9708 handles). Passes on main. */
  public void testControlSetIso8859_1() throws Exception {
    assertVerdicts("ISO8859-1");
  }

  /** ISO8859-2 (Central European; used by real Polish, Slovenian and Bosnian dictionaries). */
  public void testSetIso8859_2() throws Exception {
    assertVerdicts("ISO8859-2");
  }

  /** ISO8859-14 goes through the ISO-8859-1 path (ISO8859_14Decoder reports ISO-8859-1 as its charset). */
  public void testSetIso8859_14() throws Exception {
    assertVerdicts("ISO8859-14");
  }

  /** The stemmer side of the same merge: "kote" must have no stem. */
  public void testStemSetIso8859_2() throws Exception {
    List<CharsRef> stems = new Stemmer(load("ISO8859-2")).stem("kote");
    assertTrue("SET ISO8859-2, FLAG UTF-8: stems of kote should be empty, got " + stems, stems.isEmpty());
  }

  /** 0xC3, the UTF-8 lead byte of both flags, is unassigned in ISO-8859-3: the dictionary does not load at all. */
  public void testSetIso8859_3Loads() throws Exception {
    assertVerdicts("ISO8859-3");
  }
}
