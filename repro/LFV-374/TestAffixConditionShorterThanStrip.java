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
 * An affix condition with fewer character patterns than the strip lies entirely inside the strip.
 * hunspell(5): the condition must match the end (SFX) or the beginning (PFX) of the root word,
 * i.e. the end/beginning of the strip. Lucene checks the other end when the condition contains a
 * bracket group or a dot. The same rules written as a plain literal condition ("c" instead of
 * "[c]") work. Uses only public API (Dictionary, Hunspell).
 */
public class TestAffixConditionShorterThanStrip extends LuceneTestCase {

  private static Hunspell load(String aff, String dic) throws Exception {
    try (InputStream a = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream d = new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))) {
      return new Hunspell(new Dictionary(a, List.of(d), false, SortingStrategy.inMemory()));
    }
  }

  private static final String AFF =
      """
      SET UTF-8
      SFX A Y 2
      SFX A abc d [c]
      SFX A abc e [a]
      PFX B Y 2
      PFX B abc d [a]
      PFX B abc e [c]
      """;
  private static final String DIC = "2\nxabc/A\nabcx/B\n";

  /** SFX: root "xabc" ends with "c", so [c] holds and "xabc" - "abc" + "d" = "xd" is correct. */
  public void testSuffixConditionAtEndOfStripApplies() throws Exception {
    Hunspell h = load(AFF, DIC);
    assertTrue("xd: SFX A abc d [c] on root xabc", h.spell("xd"));
    assertEquals(List.of("xabc"), h.getRoots("xd"));
  }

  /** SFX: root "xabc" ends with "c", not "a", so [a] never holds and "xe" is not a word. */
  public void testSuffixConditionNotAtEndOfStripNeverApplies() throws Exception {
    Hunspell h = load(AFF, DIC);
    assertFalse("xe: SFX A abc e [a] on root xabc", h.spell("xe"));
  }

  /** PFX: root "abcx" starts with "a", so [a] holds and "abcx" - "abc" + "d" = "dx" is correct. */
  public void testPrefixConditionAtStartOfStripApplies() throws Exception {
    Hunspell h = load(AFF, DIC);
    assertTrue("dx: PFX B abc d [a] on root abcx", h.spell("dx"));
  }

  /** PFX: root "abcx" starts with "a", not "c", so [c] never holds and "ex" is not a word. */
  public void testPrefixConditionNotAtStartOfStripNeverApplies() throws Exception {
    Hunspell h = load(AFF, DIC);
    assertFalse("ex: PFX B abc e [c] on root abcx", h.spell("ex"));
  }

  /** Control: the same suffix rule with the literal condition "c" (no brackets) works today. */
  public void testLiteralConditionControl() throws Exception {
    Hunspell h = load("SET UTF-8\nSFX A Y 1\nSFX A abc d c\n", "1\nxabc/A\n");
    assertTrue(h.spell("xd"));
  }
}
