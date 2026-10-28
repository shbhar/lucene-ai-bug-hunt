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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * COMPOUNDBEGIN, COMPOUNDMIDDLE and COMPOUNDEND allow a word to be a part of a compound; they do
 * not restrict it to compounds (ONLYINCOMPOUND does that). The spell checker accepts such words on
 * their own, but the word form generator leaves them out.
 */
public class TestWordFormsCompoundPositionFlags extends LuceneTestCase {

  private static Dictionary dictionary(String aff, String dic) throws Exception {
    return new Dictionary(
        new ByteBuffersDirectory(),
        "dict",
        new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8)));
  }

  private static Set<String> forms(Hunspell h, String root) {
    Set<String> forms = new TreeSet<>();
    for (AffixedWord w : h.getAllWordForms(root)) {
      forms.add(w.getWord());
    }
    return forms;
  }

  private static Set<String> sorted(String... words) {
    return new TreeSet<>(List.of(words));
  }

  /** An entry with a compound position flag is also a word on its own. */
  public void testEntryWithCompoundPositionFlag() throws Exception {
    StringBuilder expected = new StringBuilder();
    StringBuilder actual = new StringBuilder();
    for (String directive : List.of("COMPOUNDBEGIN", "COMPOUNDMIDDLE", "COMPOUNDEND")) {
      String aff = "SET UTF-8\n" + directive + " B\n\nSFX A Y 1\nSFX A 0 s .\n";
      Hunspell h = new Hunspell(dictionary(aff, "1\nfoo/AB\n"));
      assertTrue(h.spell("foo"));
      assertTrue(h.spell("foos"));
      expected.append(directive).append(": [foo, foos]\n");
      actual.append(directive).append(": ").append(forms(h, "foo")).append('\n');
    }
    assertEquals(expected.toString(), actual.toString());
  }

  /** A suffix whose continuation class is COMPOUNDEND, as in the germancompounding test fixture. */
  public void testSuffixWithCompoundEndContinuation() throws Exception {
    String aff = "SET UTF-8\nCOMPOUNDEND W\n\nSFX C Y 1\nSFX C 0 n/W .\n";
    Hunspell h = new Hunspell(dictionary(aff, "1\nComputer/C\n"));
    assertTrue(h.spell("Computer"));
    assertTrue(h.spell("Computern"));
    assertEquals(sorted("Computer", "Computern"), forms(h, "Computer"));
  }

  /**
   * generateAllSimpleWords, and so NGramFragmentChecker.fromAllSimpleWords, skip the same entries.
   */
  public void testGenerateAllSimpleWords() throws Exception {
    String aff = "SET UTF-8\nCOMPOUNDBEGIN B\nCOMPOUNDEND E\n\nSFX A Y 1\nSFX A 0 s .\n";
    Dictionary d = dictionary(aff, "3\nfoo/AB\nbar/E\nqux/A\n");
    Hunspell h = new Hunspell(d);
    for (String w : List.of("foo", "foos", "bar", "foobar", "qux", "quxs")) {
      assertTrue(w, h.spell(w));
    }
    Set<String> all = new TreeSet<>();
    new WordFormGenerator(d).generateAllSimpleWords(w -> all.add(w.getWord()), () -> {});
    assertEquals(sorted("bar", "foo", "foos", "qux", "quxs"), all);
  }

  /**
   * Controls, which pass: COMPOUNDFLAG, which also only allows compounding, is expanded;
   * ONLYINCOMPOUND, which forbids the word on its own, is excluded.
   */
  public void testControls() throws Exception {
    String aff = "SET UTF-8\nCOMPOUNDFLAG Z\nONLYINCOMPOUND O\n\nSFX A Y 1\nSFX A 0 s .\n";
    Hunspell h = new Hunspell(dictionary(aff, "2\nfoo/AZ\nbaz/AO\n"));
    assertEquals(sorted("foo", "foos"), forms(h, "foo"));
    assertFalse(h.spell("baz"));
    assertEquals(sorted(), forms(h, "baz"));
  }
}
