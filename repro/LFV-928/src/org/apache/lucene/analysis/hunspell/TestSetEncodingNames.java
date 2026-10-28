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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * hunspell(5), SET: "Possible values: UTF-8, ISO8859-1 - ISO8859-10, ISO8859-13 - ISO8859-15, KOI8-R, KOI8-U, cp1251,
 * ISCII-DEVANAGARI." C Hunspell lower-cases the value and drops non-alphanumerics before its table lookup, so
 * ISO8859-14, ISO-8859-14 and iso8859-14 name the same table. Each dictionary here has one word per letter byte,
 * "x" + byte + "y"; the test spells the same word with the code point the standard mapping gives that byte.
 * Public API only (Dictionary, Hunspell).
 */
public class TestSetEncodingNames extends LuceneTestCase {

  /** {byte, expected code point} pairs for letters of each encoding (unicode.org mapping tables) */
  private static final int[][] ISO8859_2 = {{0xA1, 0x0104}, {0xB1, 0x0105}, {0xE6, 0x0107}, {0xBF, 0x017C}};
  private static final int[][] ISO8859_10 = {{0xA1, 0x0104}, {0xB1, 0x0105}, {0xBF, 0x014B}, {0xF0, 0x00F0}};
  private static final int[][] ISO8859_14 = {{0xA1, 0x1E02}, {0xA2, 0x1E03}, {0xBF, 0x1E61}, {0xD0, 0x0174}};

  private static void assertLoadsAndDecodes(String set, int[][] letters) throws Exception {
    byte[] aff = ("SET " + set + "\n").getBytes(StandardCharsets.US_ASCII);
    ByteArrayOutputStream dic = new ByteArrayOutputStream();
    dic.write((letters.length + "\n").getBytes(StandardCharsets.US_ASCII));
    for (int[] l : letters) {
      dic.write('x');
      dic.write(l[0]);
      dic.write("y\n".getBytes(StandardCharsets.US_ASCII));
    }
    Dictionary d =
        new Dictionary(
            new ByteBuffersDirectory(),
            "setenc",
            new ByteArrayInputStream(aff),
            new ByteArrayInputStream(dic.toByteArray()));
    Hunspell h = new Hunspell(d);
    for (int[] l : letters) {
      String w = "x" + new String(Character.toChars(l[1])) + "y";
      assertTrue(
          String.format("SET %s: byte 0x%02X should read as U+%04X", set, l[0], l[1]), h.spell(w));
    }
  }

  /** Control: an ISO-8859 part that the JDK knows loads in all three spellings. Passes on main. */
  public void testControlIso8859_2AllSpellings() throws Exception {
    assertLoadsAndDecodes("ISO8859-2", ISO8859_2);
    assertLoadsAndDecodes("ISO-8859-2", ISO8859_2);
    assertLoadsAndDecodes("iso8859-2", ISO8859_2);
  }

  /** Control: the exact spelling that Dictionary.getDecoder special-cases. Passes on main. */
  public void testControlIso8859_14ExactSpelling() throws Exception {
    assertLoadsAndDecodes("ISO8859-14", ISO8859_14);
  }

  public void testIso8859_14HyphenatedSpelling() throws Exception {
    assertLoadsAndDecodes("ISO-8859-14", ISO8859_14);
  }

  public void testIso8859_14LowerCaseSpelling() throws Exception {
    assertLoadsAndDecodes("iso8859-14", ISO8859_14);
  }

  /** The documented value ISO8859-10 (Latin-6, Nordic); the JDK has no charset for it under any name. */
  public void testIso8859_10() throws Exception {
    assertLoadsAndDecodes("ISO8859-10", ISO8859_10);
  }
}
