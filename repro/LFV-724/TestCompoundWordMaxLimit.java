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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * hunspell(5): "COMPOUNDWORDMAX number: Set maximum word count in a compound word." A two-word
 * compound therefore exceeds COMPOUNDWORDMAX 1. Only public API is used (Dictionary constructor,
 * Hunspell.spell).
 */
public class TestCompoundWordMaxLimit extends LuceneTestCase {

  private static final String DIC_FLAG = "3\nsun/X\nflower/X\nlight/X\n";
  private static final String DIC_BEGIN_END = "3\nsun/B\nflower/BME\nlight/E\n";

  private Hunspell load(String aff, String dic) throws Exception {
    try (Directory dir = newDirectory();
        InputStream affIn = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream dicIn = new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))) {
      return new Hunspell(new Dictionary(dir, "dictionary", affIn, dicIn));
    }
  }

  private static String flagAff(String maxLine) {
    return "SET UTF-8\nCOMPOUNDFLAG X\n" + maxLine;
  }

  private static String beginEndAff(String maxLine) {
    return "SET UTF-8\nCOMPOUNDBEGIN B\nCOMPOUNDMIDDLE M\nCOMPOUNDEND E\n" + maxLine;
  }

  // ---- the claim: limit 1 (and 0) must reject a two-word compound ----

  public void testMaxOneFlagRejectsTwoWords() throws Exception {
    Hunspell h = load(flagAff("COMPOUNDWORDMAX 1\n"), DIC_FLAG);
    assertTrue(h.spell("sun"));
    assertTrue(h.spell("flower"));
    assertFalse("3 words with COMPOUNDWORDMAX 1", h.spell("sunflowerlight"));
    assertFalse("2 words with COMPOUNDWORDMAX 1", h.spell("sunflower"));
  }

  public void testMaxOneBeginEndRejectsTwoWords() throws Exception {
    Hunspell h = load(beginEndAff("COMPOUNDWORDMAX 1\n"), DIC_BEGIN_END);
    assertTrue(h.spell("sun"));
    assertFalse("2 words with COMPOUNDWORDMAX 1 (BEGIN/END)", h.spell("sunlight"));
  }

  public void testMaxZeroRejectsTwoWords() throws Exception {
    Hunspell h = load(flagAff("COMPOUNDWORDMAX 0\n"), DIC_FLAG);
    assertTrue(h.spell("sun"));
    assertFalse("2 words with COMPOUNDWORDMAX 0", h.spell("sunflower"));
  }

  // ---- controls: limits >= 2 and the default are enforced as documented ----

  public void testControlMaxTwo() throws Exception {
    Hunspell h = load(flagAff("COMPOUNDWORDMAX 2\n"), DIC_FLAG);
    assertTrue(h.spell("sunflower"));
    assertFalse(h.spell("sunflowerlight"));
  }

  public void testControlMaxTwoBeginEnd() throws Exception {
    Hunspell h = load(beginEndAff("COMPOUNDWORDMAX 2\n"), DIC_BEGIN_END);
    assertTrue(h.spell("sunlight"));
    assertFalse(h.spell("sunflowerlight"));
  }

  public void testControlUnlimitedBeginEnd() throws Exception {
    Hunspell h = load(beginEndAff(""), DIC_BEGIN_END);
    assertTrue(h.spell("sunlight"));
    assertTrue(h.spell("sunflowerlight"));
  }

  public void testControlMaxThree() throws Exception {
    Hunspell h = load(flagAff("COMPOUNDWORDMAX 3\n"), DIC_FLAG);
    assertTrue(h.spell("sunflowerlight"));
    assertFalse(h.spell("sunflowerlightsun"));
  }

  public void testControlUnlimited() throws Exception {
    Hunspell h = load(flagAff(""), DIC_FLAG);
    assertTrue(h.spell("sunflower"));
    assertTrue(h.spell("sunflowerlightsun"));
  }
}
