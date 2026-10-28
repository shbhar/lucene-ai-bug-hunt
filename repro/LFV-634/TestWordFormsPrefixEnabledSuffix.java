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
 * A suffix that only a prefix's continuation class allows ("PFX U 0 kilo/S", the entry has no S) is
 * accepted by the spell-checker, but the word form generator never produces it.
 */
public class TestWordFormsPrefixEnabledSuffix extends LuceneTestCase {

  /** Unit prefixes: the empty prefix and "kilo", both allowing the plural suffix S. */
  private static final String UNITS_AFF =
      """
      SET UTF-8

      PFX U Y 2
      PFX U 0 0/S .
      PFX U 0 kilo/S .

      SFX S Y 1
      SFX S 0 s .
      """;

  /** A non-empty prefix "re" that allows the plural suffix S. */
  private static final String RE_AFF =
      """
      SET UTF-8

      PFX P Y 1
      PFX P 0 re/S .

      SFX S Y 1
      SFX S 0 s .
      """;

  private static Dictionary dictionary(String aff, String entry) throws Exception {
    return new Dictionary(
        new ByteBuffersDirectory(),
        "dict",
        new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream(("1\n" + entry + "\n").getBytes(StandardCharsets.UTF_8)));
  }

  private static Set<String> words(List<AffixedWord> forms) {
    Set<String> result = new TreeSet<>();
    for (AffixedWord w : forms) result.add(w.getWord());
    return result;
  }

  public void testGetAllWordForms() throws Exception {
    Hunspell h = new Hunspell(dictionary(UNITS_AFF, "ampere/U"));
    for (String w : List.of("ampere", "amperes", "kiloampere", "kiloamperes")) {
      assertTrue("spell(" + w + ")", h.spell(w));
      assertTrue(
          w + " is analysed as a form of ampere/U",
          h.analyzeSimpleWord(w).stream()
              .anyMatch(a -> a.getDictEntry().getStem().equals("ampere")));
    }
    assertEquals(
        Set.of("ampere", "amperes", "kiloampere", "kiloamperes"),
        words(h.getAllWordForms("ampere")));
  }

  public void testGenerateAllSimpleWords() throws Exception {
    Dictionary d = dictionary(RE_AFF, "work/P");
    assertTrue(new Hunspell(d).spell("reworks"));
    List<AffixedWord> all = new ArrayList<>();
    new WordFormGenerator(d).generateAllSimpleWords(all::add, () -> {});
    assertEquals(Set.of("work", "rework", "reworks"), words(all));
  }

  public void testCompress() throws Exception {
    Hunspell h = new Hunspell(dictionary(RE_AFF, "work/P"));
    assertFalse("works is not a word of this dictionary", h.spell("works"));
    // work/P already generates all three words: nothing to edit, nothing extra
    EntrySuggestion s = h.compress(List.of("work", "rework", "reworks"));
    assertEquals("toEdit=[work/P], toAdd=[], extra=[]", s.internalsToString());
  }

  /** Controls: these pass on main and must keep passing with a fix. */
  public void testControls() throws Exception {
    // The entry itself carries S: every form is generated, and exactly once.
    Hunspell h = new Hunspell(dictionary(RE_AFF, "work/PS"));
    List<String> forms = h.getAllWordForms("work").stream().map(AffixedWord::getWord).toList();
    assertEquals(forms.toString(), 4, forms.size());
    assertEquals(Set.of("work", "works", "rework", "reworks"), new TreeSet<>(forms));

    // The mirror case: a suffix whose continuation class allows a prefix.
    String mirror =
        """
        SET UTF-8

        PFX P Y 1
        PFX P 0 re .

        SFX S Y 1
        SFX S 0 s/P .
        """;
    h = new Hunspell(dictionary(mirror, "work/S"));
    assertTrue(h.spell("reworks"));
    assertEquals(Set.of("work", "works", "reworks"), words(h.getAllWordForms("work")));
  }
}
