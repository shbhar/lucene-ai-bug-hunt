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
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * CHECKCOMPOUNDPATTERN endchars beginchars replacement: "The optional replacement parameter allows
 * simplified compound form" (hunspell(5)). When the simplified boundary is not the first boundary
 * of the compound, Lucene checks the wrong characters: valid compounds are rejected, and with a
 * replacement that reappears in its own expansion spell() recurses until StackOverflowError. Uses
 * only public API (Dictionary, Hunspell).
 */
public class TestCompoundPatternReplacementAfterFirstPart extends LuceneTestCase {

  private static Hunspell load(String aff, String dic) throws Exception {
    try (InputStream a = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream d = new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))) {
      return new Hunspell(new Dictionary(a, List.of(d), false, SortingStrategy.inMemory()));
    }
  }

  /** The content of Lucene's own test dictionary checkcompoundpattern2.aff/.dic. */
  private static final String AFF2 =
      """
      COMPOUNDFLAG A
      CHECKCOMPOUNDPATTERN 2
      CHECKCOMPOUNDPATTERN o b z
      CHECKCOMPOUNDPATTERN oo ba u
      COMPOUNDMIN 1
      """;
  private static final String DIC2 = "2\nfoo/A\nbar/A\n";

  /** Control: the simplified forms of foo+bar from checkcompoundpattern2.good are accepted. */
  public void testSimplifiedTwoPartCompoundsControl() throws Exception {
    Hunspell h = load(AFF2, DIC2);
    assertTrue(h.spell("fozar"));
    assertTrue(h.spell("fur"));
    assertTrue(h.spell("barfoo"));
  }

  /** bar + (foo+bar simplified to "fozar") = "barfozar"; bar + "fur" = "barfur". */
  public void testSimplifiedFormAtSecondBoundary() throws Exception {
    Hunspell h = load(AFF2, DIC2);
    assertTrue("barfozar = bar + foo|bar with o|b -> z", h.spell("barfozar"));
    assertTrue("barfur = bar + foo|bar with oo|ba -> u", h.spell("barfur"));
  }

  /**
   * A one-word dictionary: "au" never starts with "u", so no simplified form exists and "auu" is
   * simply misspelled. Lucene throws StackOverflowError instead of returning false.
   */
  public void testNoStackOverflow() throws Exception {
    Hunspell h =
        load(
            "COMPOUNDFLAG x\nCOMPOUNDMIN 1\nCHECKCOMPOUNDPATTERN 1\nCHECKCOMPOUNDPATTERN u u u\n",
            "1\nau/x\n");
    assertTrue(h.spell("au"));
    assertFalse(h.spell("auu"));
  }
}
