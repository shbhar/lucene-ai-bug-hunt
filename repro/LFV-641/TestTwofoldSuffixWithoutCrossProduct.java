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
import java.util.TreeSet;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * Twofold suffix stripping with the example of the hunspell(5) manual: drink/X, where the suffix
 * "able" (class X) has the continuation class Y, whose suffix is "s". The manual defines the
 * cross-product field of an affix class header as the "permission to combine prefixes and
 * suffixes". No prefix is involved here, so the field must not change the result.
 */
public class TestTwofoldSuffixWithoutCrossProduct extends BaseTokenStreamTestCase {

  private static Dictionary dictionary(char crossX, char crossY) throws Exception {
    String aff =
        "SET UTF-8\n"
            + "\n"
            + "SFX X "
            + crossX
            + " 1\n"
            + "SFX X 0 able/Y .\n"
            + "\n"
            + "SFX Y "
            + crossY
            + " 1\n"
            + "SFX Y 0 s .\n";
    return new Dictionary(
        new ByteBuffersDirectory(),
        "dict",
        new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream("1\ndrink/X\n".getBytes(StandardCharsets.UTF_8)));
  }

  private static String observe(char crossX, char crossY) throws Exception {
    Hunspell h = new Hunspell(dictionary(crossX, crossY));
    // single suffix and the negative control behave the same in every variant
    assertTrue(h.spell("drinkable"));
    assertFalse(h.spell("drinks")); // "s" is only allowed after "able"
    TreeSet<String> forms = new TreeSet<>();
    for (AffixedWord w : h.getAllWordForms("drink")) forms.add(w.getWord());
    return "X="
        + crossX
        + " Y="
        + crossY
        + ": spell="
        + h.spell("drinkables")
        + " roots="
        + h.getRoots("drinkables")
        + " forms="
        + forms
        + "\n";
  }

  public void testCrossProductDoesNotGovernTwofoldSuffixes() throws Exception {
    StringBuilder expected = new StringBuilder();
    StringBuilder actual = new StringBuilder();
    for (char x : new char[] {'Y', 'N'}) {
      for (char y : new char[] {'Y', 'N'}) {
        expected.append("X=" + x + " Y=" + y);
        expected.append(": spell=true roots=[drink] forms=[drink, drinkable, drinkables]\n");
        actual.append(observe(x, y));
      }
    }
    assertEquals(expected.toString(), actual.toString());
  }

  public void testStemFilterLeavesTwofoldWordUnstemmed() throws Exception {
    HunspellStemFilter filter =
        new HunspellStemFilter(
            whitespaceMockTokenizer("drink drinkable drinkables"), dictionary('Y', 'N'));
    assertTokenStreamContents(filter, new String[] {"drink", "drink", "drink"});
  }
}
