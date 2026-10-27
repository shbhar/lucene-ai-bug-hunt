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
 * hunspell(5): in an affix condition, "Dash hasn't got special meaning", so [a-c] is the set {a, -,
 * c}. Lucene honours this where the condition is checked against the stem, but where the condition
 * overlaps the strip it checks the strip with java.util.regex, which reads [a-c] as the range a..c.
 * Uses only public API (Dictionary, Hunspell).
 */
public class TestAffixConditionDashInStripPart extends LuceneTestCase {

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
      SFX A b x [a-c]
      SFX A b y [^a-c]
      PFX B Y 2
      PFX B b x [a-c]
      PFX B b y [^a-c]
      """;
  private static final String DIC = "2\nab/A\nba/B\n";

  /** Root "ab" ends with "b", which is not in {a, -, c}: the rule "SFX A b x [a-c]" never applies. */
  public void testSuffixSetWithDashIsNotARange() throws Exception {
    assertFalse("ax", load(AFF, DIC).spell("ax"));
  }

  /** "b" is not in {a, -, c}, so the negated condition [^a-c] holds: "ab" - "b" + "y" = "ay". */
  public void testSuffixNegatedSetWithDashIsNotARange() throws Exception {
    assertTrue("ay", load(AFF, DIC).spell("ay"));
  }

  /** Same for prefixes: root "ba" starts with "b". */
  public void testPrefixSetWithDashIsNotARange() throws Exception {
    Hunspell h = load(AFF, DIC);
    assertFalse("xa", h.spell("xa"));
    assertTrue("ya", h.spell("ya"));
  }

  /**
   * Control: the same condition checked against the stem (empty strip) already treats the dash
   * literally, so "ab" + "z" is rejected and "a-" + "z" accepted.
   */
  public void testSameConditionOnStemControl() throws Exception {
    Hunspell h = load("SET UTF-8\nSFX C Y 1\nSFX C 0 z [a-c]\n", "2\nab/C\na-/C\n");
    assertFalse(h.spell("abz"));
    assertTrue(h.spell("a-z"));
  }
}
