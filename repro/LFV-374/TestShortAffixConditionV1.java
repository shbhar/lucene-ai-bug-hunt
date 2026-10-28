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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.CharsRef;

/**
 * An affix condition is matched against the root word: its end for SFX, its start for PFX. The
 * strip is part of the root. When a bracket or dot condition has fewer character patterns than the
 * strip has characters, the condition lies entirely inside the strip, so it must be compared with
 * the END of the strip for SFX and the START of the strip for PFX (as Lucene already does for
 * literal conditions, see TestAffixCondition.testUniqueKey).
 *
 * <p>Expected values below are what the reference hunspell command line tool reports for the same
 * .aff/.dic files.
 */
public class TestShortAffixConditionV1 extends LuceneTestCase {

  private static final String AFF =
      String.join(
          "\n",
          "SET UTF-8",
          "",
          "SFX A Y 1",
          "SFX A um a [m]", // root ends in m: always applies
          "",
          "SFX B Y 1",
          "SFX B um a [u]", // root ends in m, never in u: never applies
          "",
          "SFX C Y 1",
          "SFX C ium ia .m", // root ends in ?m: always applies
          "",
          "SFX D Y 1",
          "SFX D um a m", // control: literal short condition, always applies
          "",
          "SFX E Y 1",
          "SFX E um a [^m]", // root ends in m: never applies
          "",
          "SFX F Y 1",
          "SFX F um a [u]m", // control: same length as strip, always applies
          "",
          "PFX P Y 1",
          "PFX P un re [u]", // root starts with u: always applies
          "",
          "PFX Q Y 1",
          "PFX Q un re [n]", // root starts with u, never with n: never applies
          "");

  private static final String DIC =
      String.join(
          "\n",
          "8",
          "datum/A",
          "quantum/B",
          "medium/C",
          "stratum/D",
          "forum/E",
          "album/F",
          "undo/P",
          "unpack/Q",
          "");

  private static Dictionary loadDictionary() throws Exception {
    try (Directory tmp = new ByteBuffersDirectory();
        InputStream aff = new ByteArrayInputStream(AFF.getBytes(StandardCharsets.UTF_8));
        InputStream dic = new ByteArrayInputStream(DIC.getBytes(StandardCharsets.UTF_8))) {
      return new Dictionary(tmp, "dict", aff, dic);
    }
  }

  // --- controls (expected to pass) ---

  public void testControlLiteralShortSuffixCondition() throws Exception {
    Hunspell h = new Hunspell(loadDictionary());
    assertTrue("strata (SFX D um a m, root stratum)", h.spell("strata"));
    assertEquals(List.of("stratum"), h.getRoots("strata"));
  }

  public void testControlEqualLengthBracketSuffixCondition() throws Exception {
    Hunspell h = new Hunspell(loadDictionary());
    assertTrue("alba (SFX F um a [u]m, root album)", h.spell("alba"));
  }

  // --- suffix rules with a condition shorter than the strip ---

  public void testSuffixBracketConditionMatchingStripEnd() throws Exception {
    Hunspell h = new Hunspell(loadDictionary());
    assertTrue("data (SFX A um a [m], root datum)", h.spell("data"));
  }

  public void testSuffixBracketConditionMatchingStripEndRoots() throws Exception {
    Hunspell h = new Hunspell(loadDictionary());
    assertEquals(List.of("datum"), h.getRoots("data"));
  }

  public void testSuffixBracketConditionMatchingOnlyStripStart() throws Exception {
    Hunspell h = new Hunspell(loadDictionary());
    assertFalse("quanta (SFX B um a [u], root quantum)", h.spell("quanta"));
  }

  public void testSuffixDotConditionMatchingStripEnd() throws Exception {
    Hunspell h = new Hunspell(loadDictionary());
    assertTrue("media (SFX C ium ia .m, root medium)", h.spell("media"));
  }

  public void testSuffixNegatedConditionExcludingStripEnd() throws Exception {
    Hunspell h = new Hunspell(loadDictionary());
    assertFalse("fora (SFX E um a [^m], root forum)", h.spell("fora"));
  }

  public void testStemmerSuffixBracketCondition() throws Exception {
    // Stemmer is what HunspellStemFilter uses
    List<CharsRef> stems = new Stemmer(loadDictionary()).stem("data");
    assertEquals("[datum]", stems.toString());
  }

  // --- prefix rules with a condition shorter than the strip ---

  public void testPrefixBracketConditionMatchingStripStart() throws Exception {
    Hunspell h = new Hunspell(loadDictionary());
    assertTrue("redo (PFX P un re [u], root undo)", h.spell("redo"));
  }

  public void testPrefixBracketConditionMatchingOnlyStripEnd() throws Exception {
    Hunspell h = new Hunspell(loadDictionary());
    assertFalse("repack (PFX Q un re [n], root unpack)", h.spell("repack"));
  }

  // --- the compiled condition itself (same-package access, as TestAffixCondition does) ---

  public void testCompiledSuffixConditionAgreesWithLiteralKey() {
    // Lucene treats the literal condition "m" with strip "um" as always true...
    assertEquals(
        AffixCondition.ALWAYS_TRUE_KEY, AffixCondition.uniqueKey(AffixKind.SUFFIX, "um", "m"));
    // ...so the equivalent bracket condition "[m]" must accept every stem too
    assertTrue(AffixCondition.compile(AffixKind.SUFFIX, "um", "[m]", "").acceptsStem("dat"));
  }

  public void testCompiledPrefixConditionAgreesWithLiteralKey() {
    assertEquals(
        AffixCondition.ALWAYS_TRUE_KEY, AffixCondition.uniqueKey(AffixKind.PREFIX, "un", "u"));
    assertTrue(AffixCondition.compile(AffixKind.PREFIX, "un", "[u]", "").acceptsStem("do"));
  }
}
