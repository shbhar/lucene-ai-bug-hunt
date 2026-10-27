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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Compound parts matched by a COMPOUNDRULE do not get the per-entry checks that the same parts get
 * in a COMPOUNDFLAG compound: KEEPCASE on the first part, FORCEUCASE on the last part, and
 * NEEDAFFIX or FORBIDDENWORD on any part but the last. Each test builds the same compound both ways
 * and expects the same answer. Public API only (Dictionary, Hunspell).
 */
public class TestCompoundRulePartChecks extends LuceneTestCase {

  private static Hunspell load(String aff, String dic) throws Exception {
    try (InputStream a = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream d = new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))) {
      return new Hunspell(new Dictionary(new ByteBuffersDirectory(), "dict", a, d));
    }
  }

  /** hunspell(5) KEEPCASE: "Forbid uppercased and capitalized forms of words signed with KEEPCASE flags." */
  public void testKeepCaseFirstPart() throws Exception {
    Hunspell flag = load("COMPOUNDMIN 1\nKEEPCASE K\nCOMPOUNDFLAG C\n", "2\nfoo/CK\nbar/C\n");
    Hunspell rule =
        load("COMPOUNDMIN 1\nKEEPCASE K\nCOMPOUNDRULE 1\nCOMPOUNDRULE GH\n", "2\nfoo/GK\nbar/H\n");
    assertTrue(flag.spell("foobar"));
    assertTrue(rule.spell("foobar"));
    assertFalse(flag.spell("Foobar"));
    assertFalse(flag.spell("FOOBAR"));
    assertFalse("COMPOUNDRULE: Foobar, KEEPCASE first part foo", rule.spell("Foobar"));
    assertFalse("COMPOUNDRULE: FOOBAR, KEEPCASE first part foo", rule.spell("FOOBAR"));
  }

  /**
   * hunspell(5) FORCEUCASE: "Last word part of a compound with flag FORCEUCASE forces capitalization
   * of the whole compound word."
   */
  public void testForceUCaseLastPart() throws Exception {
    Hunspell flag = load("COMPOUNDMIN 1\nFORCEUCASE U\nCOMPOUNDFLAG C\n", "2\nfoo/C\nbar/CU\n");
    Hunspell rule =
        load("COMPOUNDMIN 1\nFORCEUCASE U\nCOMPOUNDRULE 1\nCOMPOUNDRULE GH\n", "2\nfoo/G\nbar/HU\n");
    assertTrue(flag.spell("Foobar"));
    assertTrue(rule.spell("Foobar"));
    assertFalse(flag.spell("foobar"));
    assertFalse("COMPOUNDRULE: foobar, FORCEUCASE last part bar", rule.spell("foobar"));
  }

  /** hunspell(5) NEEDAFFIX: "This flag signs virtual stems in the dictionary, words only valid when affixed." */
  public void testNeedAffixNonFinalPart() throws Exception {
    Hunspell flag = load("COMPOUNDMIN 1\nNEEDAFFIX N\nCOMPOUNDFLAG C\n", "2\nfoo/CN\nbar/C\n");
    Hunspell rule =
        load("COMPOUNDMIN 1\nNEEDAFFIX N\nCOMPOUNDRULE 1\nCOMPOUNDRULE GH\n", "2\nfoo/GN\nbar/H\n");
    assertFalse(flag.spell("foobar"));
    assertFalse("COMPOUNDRULE: foobar, NEEDAFFIX first part foo", rule.spell("foobar"));

    // inside the same COMPOUNDRULE dictionary: a virtual stem is rejected as the last part only
    Hunspell rule3 =
        load(
            "COMPOUNDMIN 1\nNEEDAFFIX N\nCOMPOUNDRULE 1\nCOMPOUNDRULE GH*\n",
            "3\nfoo/G\nbar/HN\nbaz/H\n");
    assertTrue(rule3.spell("foobaz"));
    assertFalse(rule3.spell("foobazbar"));
    assertFalse("COMPOUNDRULE: foobarbaz, NEEDAFFIX middle part bar", rule3.spell("foobarbaz"));
  }

  /** hunspell(5) FORBIDDENWORD: "This flag signs forbidden word form." */
  public void testForbiddenNonFinalPart() throws Exception {
    Hunspell flag = load("COMPOUNDMIN 1\nFORBIDDENWORD W\nCOMPOUNDFLAG C\n", "2\nfoo/CW\nbar/C\n");
    Hunspell rule =
        load("COMPOUNDMIN 1\nFORBIDDENWORD W\nCOMPOUNDRULE 1\nCOMPOUNDRULE GH\n", "2\nfoo/GW\nbar/H\n");
    assertFalse(rule.spell("foo"));
    assertFalse(flag.spell("foobar"));
    assertFalse("COMPOUNDRULE: foobar, FORBIDDENWORD first part foo", rule.spell("foobar"));
  }
}
