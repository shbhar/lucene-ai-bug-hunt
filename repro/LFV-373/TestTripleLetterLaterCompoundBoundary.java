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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * CHECKCOMPOUNDTRIPLE (hunspell(5)): "Forbid compounding, if compound word contains triple
 * repeating letters (e.g. foo|ox or xo|oof)." Lucene's own fixture checkcompoundtriple lists
 * "bareeel" (bare|eel, the "xo|oof" shape) and "fooopera" (foo|opera, the "foo|ox" shape) as wrong.
 *
 * <p>The same boundaries inside a compound of three or more parts must be rejected too. Only the
 * public API is used: {@link Dictionary#Dictionary(Directory, String, java.io.InputStream,
 * java.io.InputStream)} and {@link Hunspell#spell(String)}.
 */
public class TestTripleLetterLaterCompoundBoundary extends LuceneTestCase {

  private static final String AFF_TRIPLE = "CHECKCOMPOUNDTRIPLE\nCOMPOUNDFLAG A\n";
  private static final String AFF_PLAIN = "COMPOUNDFLAG A\n";
  private static final String DIC = "5\nfoo/A\nopera/A\neel/A\nbare/A\neelgrass/A\n";

  private static Hunspell load(String aff, String dic) throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      Dictionary d =
          new Dictionary(
              dir,
              "dict",
              new ByteArrayInputStream(aff.getBytes(StandardCharsets.ISO_8859_1)),
              new ByteArrayInputStream(dic.getBytes(StandardCharsets.ISO_8859_1)));
      return new Hunspell(d);
    }
  }

  private static void expect(Hunspell h, String word, boolean expected, List<String> errors) {
    boolean actual = h.spell(word);
    if (actual != expected) {
      errors.add(word + ": expected spell=" + expected + " but was " + actual);
    }
  }

  /** Controls: the triple check at the FIRST compound boundary works (Lucene's own fixture). */
  public void testFirstBoundaryControl() throws Exception {
    Hunspell h = load(AFF_TRIPLE, DIC);
    List<String> errors = new ArrayList<>();
    expect(h, "bareeel", false, errors); // bare|eel  (x|xx)
    expect(h, "fooopera", false, errors); // foo|opera (xx|x)
    expect(h, "operabare", true, errors);
    expect(h, "eelbare", true, errors);
    // three-part compounds without a triple letter are accepted, so 3-part compounding works
    expect(h, "operabarefoo", true, errors);
    expect(h, "fooeelbare", true, errors);
    assertTrue(String.join("\n", errors), errors.isEmpty());
  }

  /** Control: without CHECKCOMPOUNDTRIPLE the words used below are valid compounds. */
  public void testWordsAreValidCompoundsWithoutTripleCheck() throws Exception {
    Hunspell h = load(AFF_PLAIN, DIC);
    List<String> errors = new ArrayList<>();
    for (String w :
        new String[] {
          "bareeel", "operabareeel", "foobareeel", "barebareeel", "operafoobareeelgrass",
          "foobareeelgrass", "operafooopera"
        }) {
      expect(h, w, true, errors);
    }
    assertTrue(String.join("\n", errors), errors.isEmpty());
  }

  /** Control: the "xx|x" half of the check works at later boundaries (foo|opera inside). */
  public void testDoubleThenSingleAtLaterBoundary() throws Exception {
    Hunspell h = load(AFF_TRIPLE, DIC);
    List<String> errors = new ArrayList<>();
    expect(h, "operafooopera", false, errors); // opera + foo|opera
    expect(h, "eelfooopera", false, errors); // eel + foo|opera
    assertTrue(String.join("\n", errors), errors.isEmpty());
  }

  /** The bug: the "x|xx" half (bare|eel) is not enforced at a later boundary. */
  public void testSingleThenDoubleAtLaterBoundary() throws Exception {
    Hunspell h = load(AFF_TRIPLE, DIC);
    List<String> errors = new ArrayList<>();
    expect(h, "operabareeel", false, errors); // opera + bare|eel
    expect(h, "foobareeel", false, errors); // foo + bare|eel
    expect(h, "barebareeel", false, errors); // bare + bare|eel
    assertTrue(String.join("\n", errors), errors.isEmpty());
  }

  /**
   * Mechanism: the same bare|eel boundary is rejected or accepted depending on how far it is from
   * the start of the word compared with the length of the remaining tail.
   */
  public void testDecisionDependsOnBoundaryPosition() throws Exception {
    Hunspell h = load(AFF_TRIPLE, DIC);
    List<String> errors = new ArrayList<>();
    // short prefix, long tail: the check still happens to fire
    expect(h, "foobareeelgrass", false, errors); // foo + bare|eelgrass
    // longer prefix, same tail: the check is skipped
    expect(h, "operafoobareeelgrass", false, errors); // opera + foo + bare|eelgrass
    assertTrue(String.join("\n", errors), errors.isEmpty());
  }
}
