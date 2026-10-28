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
import java.util.TreeSet;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Every word that getAllWordForms or compress produces must be accepted by spell() of the same
 * dictionary, as the canStemToOriginal javadoc and TestSpellChecking promise. A second affix that
 * adds the same text breaks this.
 */
public class TestGeneratedWordFormsMustSpell extends LuceneTestCase {

  /** foo + A = "foos" needs one more suffix (NEEDAFFIX X); "foosen" is a word. */
  private static final String AFF =
      """
      NEEDAFFIX X

      SFX A Y 1
      SFX A 0 s/XB .

      SFX B Y 1
      SFX B 0 en .
      """;

  /** Another suffix class that also adds "s". The root foo does not have it. */
  private static final String SAME_TEXT_CLASS = "\nSFX C Y 1\nSFX C 0 s .\n";

  public void testControlWithoutSameTextClass() throws Exception {
    Hunspell h = load(AFF, "foo/A");
    assertFalse(h.spell("foos"));
    assertEquals("[foo, foosen]", forms(h, "foo"));
  }

  public void testSameTextClassLeaksRejectedForm() throws Exception {
    Hunspell h = load(AFF + SAME_TEXT_CLASS, "foo/A", "bar/C");
    assertFalse(h.spell("foos")); // still not a word
    assertTrue(h.spell("foosen"));
    assertTrue(h.spell("bars"));
    assertEquals("[foo, foosen]", forms(h, "foo"));
  }

  /** Lucene's test fixture needaffix5.aff, verbatim. */
  private static final String NEEDAFFIX5 =
      """
      # on affixes
      NEEDAFFIX X

      SFX A Y 2
      SFX A 0 suf/B .
      SFX A 0 pseudosuf/XB .

      SFX B Y 1
      SFX B 0 bar .

      PFX C Y 2
      PFX C 0 pre .
      PFX C 0 pseudopre/X .
      """;

  /** needaffix5 plus two classes with the same affix texts that only the new root baz uses. */
  public void testNeedaffix5PlusSameTextClasses() throws Exception {
    String aff =
        NEEDAFFIX5 + "\nSFX D Y 1\nSFX D 0 pseudosuf .\n" + "\nPFX E Y 1\nPFX E 0 pseudopre .\n";
    Hunspell h = load(aff, "foo/AC", "baz/DE");
    assertEquals("generated but rejected by spell()", "[]", rejected(h, "foo"));
  }

  /** compress() must suggest entries that accept every requested word. */
  public void testCompressSuggestionAcceptsRequestedWords() throws Exception {
    String aff = AFF + SAME_TEXT_CLASS;
    List<String> words = List.of("foo", "foos", "foosen");
    EntrySuggestion s = load(aff, "bar/C").compress(words);
    List<String> entries = new ArrayList<>(List.of("bar/C"));
    for (DictEntry e : s.getEntriesToEdit()) entries.add(e.toString());
    for (DictEntry e : s.getEntriesToAdd()) entries.add(e.toString());
    Hunspell edited = load(aff, entries.toArray(new String[0]));
    TreeSet<String> notAccepted = new TreeSet<>();
    for (String w : words) if (!edited.spell(w)) notAccepted.add(w);
    assertEquals("after applying " + s, "[]", notAccepted.toString());
  }

  private static Hunspell load(String aff, String... entries) throws Exception {
    String dic = entries.length + "\n" + String.join("\n", entries) + "\n";
    return new Hunspell(
        new Dictionary(
            new ByteBuffersDirectory(),
            "dict",
            new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8)),
            new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))));
  }

  private static String forms(Hunspell h, String root) {
    TreeSet<String> words = new TreeSet<>();
    for (AffixedWord w : h.getAllWordForms(root)) words.add(w.getWord());
    return words.toString();
  }

  private static String rejected(Hunspell h, String root) {
    TreeSet<String> bad = new TreeSet<>();
    for (AffixedWord w : h.getAllWordForms(root)) {
      if (!h.spell(w.getWord())) bad.add(w.getWord());
    }
    return bad.toString();
  }
}
