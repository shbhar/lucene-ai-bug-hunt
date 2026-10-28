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
package org.apache.lucene.analysis.hunspell.v1;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.lucene.analysis.hunspell.Dictionary;
import org.apache.lucene.analysis.hunspell.Hunspell;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check (public API only, a different package than the hunspell classes): a
 * CHECKCOMPOUNDPATTERN replacement ("simplified compound form") that occurs at the second or a
 * later compound boundary.
 */
public class TestSimplifiedCompoundLaterBoundary extends LuceneTestCase {

  private static Hunspell load(String aff, String dic) throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        InputStream a = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream d = new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))) {
      return new Hunspell(new Dictionary(dir, "v1", a, d));
    }
  }

  // Same content as Lucene's own test fixture checkcompoundpattern2.aff/.dic
  private static final String AFF2 =
      "COMPOUNDFLAG A\n"
          + "CHECKCOMPOUNDPATTERN 2\n"
          + "CHECKCOMPOUNDPATTERN o b z\n"
          + "CHECKCOMPOUNDPATTERN oo ba u\n"
          + "COMPOUNDMIN 1\n";
  private static final String DIC2 = "2\nfoo/A\nbar/A\n";

  // Same content as Lucene's own test fixture checkcompoundpattern3.aff/.dic
  private static final String AFF3 =
      "COMPOUNDFLAG A\n"
          + "CHECKCOMPOUNDPATTERN 1\n"
          + "CHECKCOMPOUNDPATTERN o/X b/Y z\n"
          + "COMPOUNDMIN 1\n";
  private static final String DIC3 = "4\nfoo/A\nboo/AX\nbar/A\nban/AY\n";

  /** Controls: the upstream fixture's good/wrong words (the 2-part simplified forms work). */
  public void testControls() throws Exception {
    Hunspell h2 = load(AFF2, DIC2);
    assertTrue(h2.spell("barfoo"));
    assertTrue(h2.spell("fozar")); // foo+bar, "o|b" simplified to "z"
    assertTrue(h2.spell("fur")); // foo+bar, "oo|ba" simplified to "u"
    assertFalse(h2.spell("foobar")); // forbidden pattern o|b
    assertTrue(h2.spell("barbar")); // ordinary 2-part compound
    assertTrue(h2.spell("barbarfoo")); // ordinary 3-part compound

    Hunspell h3 = load(AFF3, DIC3);
    assertTrue(h3.spell("bozan")); // boo+ban simplified
    assertTrue(h3.spell("boobarfoo"));
  }

  /** bar + (foo|bar simplified to fozar): the simplified boundary is the 2nd boundary. */
  public void testSimplifiedFormAtSecondBoundaryPattern2() throws Exception {
    Hunspell h = load(AFF2, DIC2);
    assertTrue("barfozar = bar + foo + bar (o|b -> z)", h.spell("barfozar"));
  }

  /** bar + (foo|bar simplified to fur) with the second pattern. */
  public void testSimplifiedFormAtSecondBoundaryPattern2b() throws Exception {
    Hunspell h = load(AFF2, DIC2);
    assertTrue("barfur = bar + foo + bar (oo|ba -> u)", h.spell("barfur"));
  }

  /** bar + (boo|ban simplified to bozan), checkcompoundpattern3 content. */
  public void testSimplifiedFormAtSecondBoundaryPattern3() throws Exception {
    Hunspell h = load(AFF3, DIC3);
    assertTrue("barbozan = bar + boo + ban (o/X|b/Y -> z)", h.spell("barbozan"));
  }

  // "uu" at a compound boundary may be written as "u"
  private static final String AFF_U =
      "COMPOUNDFLAG x\n" + "COMPOUNDMIN 1\n" + "CHECKCOMPOUNDPATTERN 1\n" + "CHECKCOMPOUNDPATTERN u u u\n";
  private static final String DIC_U = "1\nau/x\n";

  /** spell() must return a boolean; "auu" cannot be split into dictionary words. */
  public void testSpellDoesNotOverflow() throws Exception {
    Hunspell h = load(AFF_U, DIC_U);
    assertTrue(h.spell("au"));
    boolean result;
    try {
      result = h.spell("auu");
    } catch (StackOverflowError e) {
      throw new AssertionError("spell(\"auu\") threw StackOverflowError", e);
    }
    assertFalse(result);
  }

  // Content of the C Hunspell test fixture checkcompoundpattern4 (Telugu sandhi example)
  private static final String AFF4 =
      "COMPOUNDFLAG x\n"
          + "COMPOUNDMIN 1\n"
          + "CHECKCOMPOUNDPATTERN 2\n"
          + "CHECKCOMPOUNDPATTERN a/A u/A O\n"
          + "CHECKCOMPOUNDPATTERN u/B u/B u\n";
  private static final String DIC4 = "4\nsUrya/Ax\nudayaM/Ax\npEru/Bx\nunna/Bx\n";

  /** Fixture content: good/wrong words of the fixture, then a word with a hyphen (BREAK). */
  public void testSandhiFixtureDoesNotOverflow() throws Exception {
    Hunspell h = load(AFF4, DIC4);
    assertTrue(h.spell("sUryOdayaM"));
    assertTrue(h.spell("pErunna"));
    assertFalse(h.spell("sUryaudayaM"));
    assertFalse(h.spell("pEruunna"));
    assertTrue(h.spell("sUrya"));
    assertTrue(h.spell("pEru"));
    boolean result;
    try {
      result = h.spell("sUrya-pEru"); // default BREAK splits at '-': sUrya + pEru
    } catch (StackOverflowError e) {
      throw new AssertionError("spell(\"sUrya-pEru\") threw StackOverflowError", e);
    }
    assertTrue(result);
  }

  /** suggest() must return a list for a short misspelled word. */
  public void testSuggestDoesNotOverflow() throws Exception {
    Hunspell h = load(AFF_U, DIC_U);
    for (String w : new String[] {"auu", "auxu"}) {
      try {
        List<String> s = h.suggest(w);
        assertNotNull(s);
      } catch (StackOverflowError e) {
        throw new AssertionError("suggest(\"" + w + "\") threw StackOverflowError", e);
      }
    }
  }
}
