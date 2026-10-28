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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A suffix flag that the entry itself does not carry, but that the continuation class of one of
 * its prefixes carries ("PFX P 0 re/S"), is accepted by the spell-checker (Stemmer, LUCENE-9688).
 * The word form generator should then also produce the word, since it promises to "generate all
 * word forms for all dictionary entries with the given root word".
 *
 * <p>Uses only public API (Dictionary, Hunspell, WordFormGenerator, AffixedWord); the class sits in
 * the hunspell package like Lucene's own hunspell tests but calls no package-private member.
 */
public class TestPrefixEnabledSuffixWordForms extends LuceneTestCase {

  private static Hunspell load(String aff, String dic) throws Exception {
    return new Hunspell(loadDictionary(aff, dic));
  }

  private static Dictionary loadDictionary(String aff, String dic) throws Exception {
    return new Dictionary(
            new ByteBuffersDirectory(),
            "v1",
            new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8)),
            new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8)));
  }

  private static Set<String> words(List<AffixedWord> forms) {
    Set<String> s = new TreeSet<>();
    for (AffixedWord w : forms) s.add(w.getWord());
    return s;
  }

  private static boolean analysedAsFormOf(Hunspell h, String word, String stem) {
    for (AffixedWord w : h.analyzeSimpleWord(word)) {
      if (w.getDictEntry().getStem().equals(stem)) return true;
    }
    return false;
  }

  // Non-empty prefix whose continuation class carries the suffix flag.
  static final String AFF_RE =
      String.join(
          "\n",
          "SET UTF-8",
          "PFX P Y 1",
          "PFX P 0 re/S .",
          "SFX S Y 1",
          "SFX S 0 s .",
          "");
  static final String DIC_RE = "1\nwork/P\n";

  // Zero-length prefix whose continuation class carries the suffix flag (the pattern of the
  // upstream fixture dependencies.aff, 'PFX p 0 0/s').
  static final String AFF_ZERO =
      String.join(
          "\n",
          "SET UTF-8",
          "PFX Z Y 1",
          "PFX Z 0 0/S .",
          "SFX S Y 1",
          "SFX S 0 s .",
          "");
  static final String DIC_ZERO = "1\nbook/Z\n";

  public void testNonEmptyPrefixEnablesSuffix() throws Exception {
    Hunspell h = load(AFF_RE, DIC_RE);
    // Preconditions: what the spell-checker / analyser of the same dictionary accept.
    assertTrue(h.spell("work"));
    assertTrue(h.spell("rework"));
    assertTrue("spell-checker accepts reworks", h.spell("reworks"));
    assertFalse("works is not a form (work has no S, prefix not applied)", h.spell("works"));
    assertTrue("reworks is analysed as a form of work", analysedAsFormOf(h, "reworks", "work"));

    Set<String> generated = words(h.getAllWordForms("work"));
    assertEquals(new TreeSet<>(List.of("rework", "reworks", "work")), generated);
  }

  public void testZeroPrefixEnablesSuffix() throws Exception {
    Hunspell h = load(AFF_ZERO, DIC_ZERO);
    assertTrue(h.spell("book"));
    assertTrue("spell-checker accepts books", h.spell("books"));
    assertTrue("books is analysed as a form of book", analysedAsFormOf(h, "books", "book"));

    Set<String> generated = words(h.getAllWordForms("book"));
    assertTrue("getAllWordForms(book) = " + generated, generated.contains("books"));
  }

  public void testGenerateAllSimpleWordsMissesIt() throws Exception {
    Dictionary d = loadDictionary(AFF_RE, DIC_RE);
    Hunspell h = new Hunspell(d);
    List<String> all = new ArrayList<>();
    new WordFormGenerator(d).generateAllSimpleWords(w -> all.add(w.getWord()), () -> {});
    assertTrue("spell-checker accepts reworks", h.spell("reworks"));
    assertTrue("generateAllSimpleWords = " + all, all.contains("reworks"));
  }

  /**
   * Control (expected to PASS): the mirror case, a prefix enabled by a suffix's continuation class
   * ("SFX S 0 s/P"), is generated. Shows the generator handles continuation classes in one
   * direction only.
   */
  public void testControlSuffixEnablesPrefixIsGenerated() throws Exception {
    String aff =
        String.join(
            "\n",
            "SET UTF-8",
            "PFX P Y 1",
            "PFX P 0 re .",
            "SFX S Y 1",
            "SFX S 0 s/P .",
            "");
    Hunspell h = load(aff, "1\nwork/S\n");
    assertTrue(h.spell("reworks"));
    assertFalse(h.spell("rework"));
    assertEquals(
        new TreeSet<>(List.of("reworks", "work", "works")), words(h.getAllWordForms("work")));
  }

  /**
   * Secondary manifestation (informational): the existing entry work/P already generates all three
   * words per the spell-checker, yet compress proposes adding flag S to it.
   */
  public void testCompressOfAlreadyCoveredWords() throws Exception {
    Hunspell h = load(AFF_RE, DIC_RE);
    EntrySuggestion s = h.compress(List.of("work", "rework", "reworks"));
    assertEquals("toEdit=[work/P], toAdd=[], extra=[]", s.internalsToString());
  }
}
