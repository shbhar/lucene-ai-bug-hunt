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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: WordFormGenerator / Hunspell#getAllWordForms on entries (or affix continuation
 * classes) that carry COMPOUNDBEGIN / COMPOUNDMIDDLE / COMPOUNDEND. Those flags only permit a word to
 * be a compound part; they do not restrict the word to compounds (that is ONLYINCOMPOUND). The
 * spell-checker itself accepts the standalone forms. Uses public API only.
 */
public class TestCompoundFlagWordForms extends LuceneTestCase {

  private static Hunspell load(String aff, String dic) throws Exception {
    try (Directory tmp = new ByteBuffersDirectory();
        InputStream a = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream d = new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))) {
      return new Hunspell(new Dictionary(tmp, "v1", a, d));
    }
  }

  private static Set<String> forms(Hunspell h, String root) {
    Set<String> out = new TreeSet<>();
    for (AffixedWord w : h.getAllWordForms(root)) {
      out.add(w.getWord());
    }
    return out;
  }

  private static void assertAllSpelledAndSimple(Hunspell h, String... words) {
    for (String w : words) {
      assertTrue("spell() should accept " + w, h.spell(w));
      assertFalse(
          "analyzeSimpleWord() should analyze " + w + " as a simple (non-compound) word",
          h.analyzeSimpleWord(w).isEmpty());
    }
  }

  // ---- the claim ----------------------------------------------------------------------------

  /** Root entry carries COMPOUNDBEGIN. */
  public void testRootWithCompoundBegin() throws Exception {
    Hunspell h =
        load(
            "SET UTF-8\nCOMPOUNDBEGIN X\nSFX S Y 1\nSFX S 0 s .\n", //
            "1\nfoo/SX\n");
    assertAllSpelledAndSimple(h, "foo", "foos");
    assertEquals(Set.of("foo", "foos"), forms(h, "foo"));
  }

  /** Root entry carries COMPOUNDMIDDLE. */
  public void testRootWithCompoundMiddle() throws Exception {
    Hunspell h =
        load(
            "SET UTF-8\nCOMPOUNDMIDDLE M\nSFX S Y 1\nSFX S 0 s .\n", //
            "1\nbar/SM\n");
    assertAllSpelledAndSimple(h, "bar", "bars");
    assertEquals(Set.of("bar", "bars"), forms(h, "bar"));
  }

  /** Root entry carries COMPOUNDEND. */
  public void testRootWithCompoundEnd() throws Exception {
    Hunspell h =
        load(
            "SET UTF-8\nCOMPOUNDEND E\nSFX S Y 1\nSFX S 0 s .\n", //
            "1\nbaz/SE\n");
    assertAllSpelledAndSimple(h, "baz", "bazs");
    assertEquals(Set.of("baz", "bazs"), forms(h, "baz"));
  }

  /** A suffix whose continuation class is COMPOUNDEND (as in germancompounding: SFX C 0 n/WD). */
  public void testSuffixContinuationCompoundEnd() throws Exception {
    Hunspell h =
        load(
            "SET UTF-8\nCOMPOUNDEND W\nSFX C Y 1\nSFX C 0 n/W .\n", //
            "1\ncomputer/C\n");
    assertAllSpelledAndSimple(h, "computer", "computern");
    assertEquals(Set.of("computer", "computern"), forms(h, "computer"));
  }

  /** WordFormGenerator.generateAllSimpleWords over the same kind of dictionary. */
  public void testGenerateAllSimpleWordsSkipsCompoundBeginEntry() throws Exception {
    Hunspell h =
        load(
            "SET UTF-8\nCOMPOUNDBEGIN X\nSFX S Y 1\nSFX S 0 s .\n", //
            "2\nfoo/SX\nqux/S\n");
    assertAllSpelledAndSimple(h, "foo", "foos", "qux", "quxs");
    Set<String> all = new TreeSet<>();
    new WordFormGenerator(h.dictionary).generateAllSimpleWords(w -> all.add(w.getWord()), () -> {});
    assertEquals(Set.of("foo", "foos", "qux", "quxs"), all);
  }

  /**
   * Downstream: NGramFragmentChecker.fromAllSimpleWords is built from generateAllSimpleWords. Its
   * contract (FragmentChecker) is an oracle that a fragment "can never be a valid word"; here it
   * calls fragments of the valid simple word "foos" impossible.
   */
  public void testFragmentCheckerFromAllSimpleWordsRejectsValidWord() throws Exception {
    Hunspell h =
        load(
            "SET UTF-8\nCOMPOUNDBEGIN X\nSFX S Y 1\nSFX S 0 s .\n", //
            "2\nfoo/SX\nqux/S\n");
    assertTrue(h.spell("foos"));
    NGramFragmentChecker checker = NGramFragmentChecker.fromAllSimpleWords(2, h.dictionary, () -> {});
    List<Integer> impossibleAt = new ArrayList<>();
    String w = "foos";
    for (int i = 0; i < w.length(); i++) {
      if (checker.hasImpossibleFragmentAround(w, i, i + 1)) impossibleAt.add(i);
    }
    assertEquals("positions of the valid word 'foos' flagged impossible", List.of(), impossibleAt);
  }

  // ---- controls (expected to pass on every version) -----------------------------------------

  /** COMPOUNDFLAG (any compound position) is NOT excluded: forms are generated. */
  public void testControlCompoundFlagRootIsExpanded() throws Exception {
    Hunspell h =
        load(
            "SET UTF-8\nCOMPOUNDFLAG Z\nSFX S Y 1\nSFX S 0 s .\n", //
            "1\nfoo/SZ\n");
    assertAllSpelledAndSimple(h, "foo", "foos");
    assertEquals(Set.of("foo", "foos"), forms(h, "foo"));
  }

  /** ONLYINCOMPOUND root: not a standalone word, and nothing is generated (correct). */
  public void testControlOnlyInCompoundRootNotStandalone() throws Exception {
    Hunspell h =
        load(
            "SET UTF-8\nONLYINCOMPOUND O\nSFX S Y 1\nSFX S 0 s .\n", //
            "1\nfoo/SO\n");
    assertFalse(h.spell("foo"));
    assertEquals(Set.of(), forms(h, "foo"));
  }

  /** Same dictionary without any compound flag: forms are generated. */
  public void testControlNoCompoundFlag() throws Exception {
    Hunspell h =
        load(
            "SET UTF-8\nCOMPOUNDBEGIN X\nSFX S Y 1\nSFX S 0 s .\n", //
            "1\nfoo/S\n");
    assertEquals(Set.of("foo", "foos"), forms(h, "foo"));
  }
}
