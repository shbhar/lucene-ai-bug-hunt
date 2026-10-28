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
 * An affix condition with fewer characters than the strip lies inside the strip. A suffix condition
 * has to match the end of the root word, a prefix condition its start. Lucene checks the other end
 * of the strip when the condition uses brackets or a dot.
 */
public class TestShortAffixCondition extends LuceneTestCase {

  private static final String AFF =
      """
      SET UTF-8
      SFX A Y 1
      SFX A um a [m]
      SFX B Y 1
      SFX B um a [u]
      SFX D Y 1
      SFX D um a m
      PFX P Y 1
      PFX P un re [u]
      """;
  private static final String DIC = "4\ndatum/A\nquantum/B\nstratum/D\nundo/P\n";

  private static Hunspell load() throws Exception {
    try (InputStream aff = new ByteArrayInputStream(AFF.getBytes(StandardCharsets.UTF_8));
        InputStream dic = new ByteArrayInputStream(DIC.getBytes(StandardCharsets.UTF_8))) {
      return new Hunspell(new Dictionary(aff, List.of(dic), false, SortingStrategy.inMemory()));
    }
  }

  /** Control: with the literal condition "m", stratum gives strata. */
  public void testLiteralCondition() throws Exception {
    Hunspell h = load();
    assertTrue("strata", h.spell("strata"));
    assertEquals(List.of("stratum"), h.getRoots("strata"));
  }

  /** datum ends in m, so [m] holds: data is a correct word with the root datum. */
  public void testSuffixConditionMet() throws Exception {
    Hunspell h = load();
    assertTrue("data", h.spell("data"));
    assertEquals(List.of("datum"), h.getRoots("data"));
  }

  /** quantum ends in m, not u, so [u] never holds: quanta is not a word. */
  public void testSuffixConditionNotMet() throws Exception {
    Hunspell h = load();
    assertFalse("quanta", h.spell("quanta"));
  }

  /** undo starts with u, so [u] holds: redo is a correct word with the root undo. */
  public void testPrefixConditionMet() throws Exception {
    Hunspell h = load();
    assertTrue("redo", h.spell("redo"));
    assertEquals(List.of("undo"), h.getRoots("redo"));
  }
}
