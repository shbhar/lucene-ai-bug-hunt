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
 * CHECKCOMPOUNDPATTERN with a replacement: "The optional replacement parameter allows simplified
 * compound form" (hunspell(5)). The dictionary is the example of the Hunspell ChangeLog: "ff f ff"
 * means that "ff" at a compound join stands for "ff|f", as in the pre-reform German Schiffahrt
 * (Schiff + Fahrt). Public API only.
 */
public class TestCheckCompoundPatternLaterJoin extends LuceneTestCase {

  private static final String AFF =
      """
      COMPOUNDFLAG A
      COMPOUNDMIN 1
      CHECKCOMPOUNDPATTERN 1
      CHECKCOMPOUNDPATTERN ff f ff
      """;
  private static final String DIC = "4\ndampf/A\nschiff/A\nfahrt/A\ndonau/A\n";

  private static Hunspell load() throws Exception {
    try (InputStream a = new ByteArrayInputStream(AFF.getBytes(StandardCharsets.UTF_8));
        InputStream d = new ByteArrayInputStream(DIC.getBytes(StandardCharsets.UTF_8))) {
      return new Hunspell(new Dictionary(a, List.of(d), false, SortingStrategy.inMemory()));
    }
  }

  /** Control: the simplified join is the first join of the compound. */
  public void testSimplifiedFirstJoin() throws Exception {
    assertTrue("schiff|fahrt", load().spell("schiffahrt"));
  }

  /** The same simplified join after one or two other parts. */
  public void testSimplifiedLaterJoin() throws Exception {
    Hunspell h = load();
    assertTrue("dampf + schiff|fahrt", h.spell("dampfschiffahrt"));
    assertTrue("donau + dampf + schiff|fahrt", h.spell("donaudampfschiffahrt"));
  }

  /** spell() must return true or false. */
  public void testSpellReturnsAVerdict() throws Exception {
    load().spell("schiffschiffahrt");
  }
}
