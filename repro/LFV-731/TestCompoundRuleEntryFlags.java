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
 * Per-entry flags (KEEPCASE, FORCEUCASE, NEEDAFFIX, FORBIDDENWORD, COMPOUNDFORBIDFLAG) on the parts
 * of a compound built by COMPOUNDRULE. Every word asserted false here is rejected by C Hunspell
 * 1.7.2 on the same dictionary, and every word asserted true is accepted by it. Most tests also
 * check that Lucene rejects the word when the same parts are joined by COMPOUNDFLAG instead.
 */
public class TestCompoundRuleEntryFlags extends LuceneTestCase {

  private static Hunspell load(String aff, String dic) throws Exception {
    try (InputStream a = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream d = new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))) {
      return new Hunspell(new Dictionary(new ByteBuffersDirectory(), "dict", a, d));
    }
  }

  private static final String RULE_GH = "COMPOUNDMIN 1\nCOMPOUNDRULE 1\nCOMPOUNDRULE GH\n";
  private static final String FLAG_C = "COMPOUNDMIN 1\nCOMPOUNDFLAG C\n";

  /** KEEPCASE: "Forbid uppercased and capitalized forms of words signed with KEEPCASE flags". */
  public void testKeepCaseFirstPart() throws Exception {
    Hunspell rule = load("KEEPCASE K\n" + RULE_GH, "2\nfoo/GK\nbar/H\n");
    Hunspell flag = load("KEEPCASE K\n" + FLAG_C, "2\nfoo/CK\nbar/C\n");
    assertTrue(rule.spell("foobar"));
    assertFalse(flag.spell("Foobar"));
    assertFalse("rule compound Foobar, first part foo is KEEPCASE", rule.spell("Foobar"));
    assertFalse("rule compound FOOBAR, first part foo is KEEPCASE", rule.spell("FOOBAR"));
  }

  /**
   * FORCEUCASE: "Last word part of a compound with flag FORCEUCASE forces capitalization of the
   * whole compound word". A miniature of the Swedish sv_SE dictionary: tolv + kronors + gatan.
   */
  public void testForceUCaseLastPart() throws Exception {
    Hunspell rule =
        load(
            "COMPOUNDMIN 1\nFORCEUCASE c\nCOMPOUNDRULE 1\nCOMPOUNDRULE 23Y\n",
            "3\ntolv/2\nkronors/3\ngatan/cY\n");
    Hunspell flag = load("FORCEUCASE c\n" + FLAG_C, "3\ntolv/C\nkronors/C\ngatan/cC\n");
    assertTrue(rule.spell("Tolvkronorsgatan"));
    assertFalse(flag.spell("tolvkronorsgatan"));
    assertFalse(
        "rule compound tolvkronorsgatan, last part gatan is FORCEUCASE",
        rule.spell("tolvkronorsgatan"));
  }

  /** NEEDAFFIX: "virtual stems in the dictionary, words only valid when affixed". */
  public void testNeedAffixFirstPart() throws Exception {
    Hunspell rule = load("NEEDAFFIX N\n" + RULE_GH, "2\nfoo/GN\nbar/H\n");
    Hunspell flag = load("NEEDAFFIX N\n" + FLAG_C, "2\nfoo/CN\nbar/C\n");
    assertFalse(flag.spell("foobar"));
    assertFalse("rule compound foobar, first part foo is NEEDAFFIX", rule.spell("foobar"));
  }

  public void testNeedAffixMiddlePart() throws Exception {
    Hunspell rule =
        load(
            "COMPOUNDMIN 1\nNEEDAFFIX N\nCOMPOUNDRULE 1\nCOMPOUNDRULE GH*\n",
            "3\nfoo/G\nbar/HN\nbaz/H\n");
    assertTrue(rule.spell("foobaz"));
    // the same entry as the LAST part is already rejected: Lucene is inconsistent with itself
    assertFalse(rule.spell("foobazbar"));
    assertFalse("rule compound foobarbaz, middle part bar is NEEDAFFIX", rule.spell("foobarbaz"));
  }

  /** FORBIDDENWORD: "This flag signs forbidden word form". */
  public void testForbiddenWordFirstPart() throws Exception {
    Hunspell rule = load("FORBIDDENWORD W\n" + RULE_GH, "2\nfoo/GW\nbar/H\n");
    Hunspell flag = load("FORBIDDENWORD W\n" + FLAG_C, "2\nfoo/CW\nbar/C\n");
    assertFalse(rule.spell("foo"));
    assertFalse(flag.spell("foobar"));
    assertFalse("rule compound foobar, first part foo is FORBIDDENWORD", rule.spell("foobar"));
  }

  /**
   * COMPOUNDFORBIDFLAG: "Dictionary words with this flag are removed from the beginning and middle
   * of compound words".
   */
  public void testCompoundForbidFlagFirstPart() throws Exception {
    Hunspell rule = load("COMPOUNDFORBIDFLAG F\n" + RULE_GH, "2\nfoo/GF\nbar/H\n");
    Hunspell flag = load("COMPOUNDFORBIDFLAG F\n" + FLAG_C, "2\nfoo/CF\nbar/C\n");
    assertTrue(rule.spell("foo"));
    assertFalse(flag.spell("foobar"));
    assertFalse("rule compound foobar, first part foo is COMPOUNDFORBIDFLAG", rule.spell("foobar"));
  }
}
