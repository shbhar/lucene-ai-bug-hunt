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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.List;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.CharsRef;

/**
 * FLAG UTF-8 flags are UTF-8 characters whatever the SET encoding of the words. Two suffix classes:
 * flag á (U+00E1, bytes C3 A1) adds "a", flag â (U+00E2, bytes C3 A2) adds "e"; "kot" has only flag
 * á and "las" only flag â. The words are ASCII, so the file bytes are the same for every SET value.
 */
public class TestFlagUtf8WithEightBitSet extends LuceneTestCase {

  private static Dictionary load(String set) throws Exception {
    ByteArrayOutputStream aff = new ByteArrayOutputStream();
    aff.writeBytes(("SET " + set + "\nFLAG UTF-8\n").getBytes(US_ASCII));
    aff.writeBytes("SFX á Y 1\nSFX á 0 a .\nSFX â Y 1\nSFX â 0 e .\n".getBytes(UTF_8));
    byte[] dic = "2\nkot/á\nlas/â\n".getBytes(UTF_8);
    return new Dictionary(
        new ByteBuffersDirectory(),
        "dictionary",
        new ByteArrayInputStream(aff.toByteArray()),
        new ByteArrayInputStream(dic));
  }

  private static void assertFlagsKeptApart(String set) throws Exception {
    Hunspell hunspell = new Hunspell(load(set));
    for (String word : new String[] {"kot", "kota", "las", "lase"}) {
      assertTrue("SET " + set + ": " + word, hunspell.spell(word));
    }
    for (String word : new String[] {"kote", "lasa"}) {
      assertFalse("SET " + set + ": " + word + " must be rejected", hunspell.spell(word));
    }
  }

  /** Works today: LUCENE-9708 decodes UTF-8 flags for the default ISO-8859-1 encoding. */
  public void testSetIso8859_1() throws Exception {
    assertFlagsKeptApart("ISO8859-1");
  }

  public void testSetIso8859_2() throws Exception {
    assertFlagsKeptApart("ISO8859-2");
  }

  /** ISO8859-15 decodes C3, A1 and A2 exactly like ISO8859-1. */
  public void testSetIso8859_15() throws Exception {
    assertFlagsKeptApart("ISO8859-15");
  }

  public void testCyrillicSets() throws Exception {
    for (String set : new String[] {"ISO8859-5", "KOI8-R", "microsoft-cp1251"}) {
      assertFlagsKeptApart(set);
    }
  }

  /** ISO8859_14Decoder reports ISO-8859-1 as its charset. */
  public void testSetIso8859_14() throws Exception {
    assertFlagsKeptApart("ISO8859-14");
  }

  /** Words in the SET encoding next to UTF-8 flags. */
  public void testSetIso8859_2WordsWithUtf8Flags() throws Exception {
    Charset latin2 = Charset.forName("ISO-8859-2");
    ByteArrayOutputStream aff = new ByteArrayOutputStream();
    aff.writeBytes("SET ISO8859-2\nFLAG UTF-8\n".getBytes(US_ASCII));
    aff.writeBytes("SFX á Y 1\nSFX á 0 a .\nSFX â Y 1\nSFX â 0 e .\n".getBytes(UTF_8));
    ByteArrayOutputStream dic = new ByteArrayOutputStream();
    dic.writeBytes("1\nżółw/".getBytes(latin2));
    dic.writeBytes("á\n".getBytes(UTF_8));
    Hunspell hunspell =
        new Hunspell(
            new Dictionary(
                new ByteBuffersDirectory(),
                "dictionary",
                new ByteArrayInputStream(aff.toByteArray()),
                new ByteArrayInputStream(dic.toByteArray())));
    assertTrue("żółw", hunspell.spell("żółw"));
    assertTrue("żółwa", hunspell.spell("żółwa"));
    assertFalse("żółwe must be rejected", hunspell.spell("żółwe"));
  }

  public void testStemmerSetIso8859_2() throws Exception {
    Stemmer stemmer = new Stemmer(load("ISO8859-2"));
    assertEquals(List.of(new CharsRef("kot")), stemmer.stem("kota"));
    assertEquals(List.of(), stemmer.stem("kote"));
  }

  /** C3, the first byte of both flags, is unassigned in ISO8859-3. */
  public void testSetIso8859_3() throws Exception {
    assertFlagsKeptApart("ISO8859-3");
  }
}
