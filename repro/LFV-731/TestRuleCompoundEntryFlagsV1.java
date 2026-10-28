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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent verifier repro (public API only: Dictionary(Directory, String, InputStream,
 * InputStream) and Hunspell.spell(String)).
 *
 * <p>Claim under test: the parts of a compound joined by a COMPOUNDRULE are not subject to the
 * per-entry directives that Lucene applies to the parts of a COMPOUNDFLAG compound (KEEPCASE on the
 * first part, FORCEUCASE on the last part, NEEDAFFIX and FORBIDDENWORD on a non-final part).
 *
 * <p>Each test first checks the COMPOUNDFLAG twin dictionary (same words, same directive, parts
 * joined by COMPOUNDFLAG X instead of COMPOUNDRULE PQ) and a positive control, then asserts that the
 * COMPOUNDRULE dictionary gives the same answer.
 */
public class TestRuleCompoundEntryFlagsV1 extends LuceneTestCase {

  private Hunspell load(String aff, String dic) throws Exception {
    try (Directory dir = newDirectory();
        InputStream a = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream d = new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))) {
      return new Hunspell(new Dictionary(dir, "dict", a, d));
    }
  }

  private static String ruleAff(String extra) {
    return "SET UTF-8\n" + extra + "\nCOMPOUNDRULE 1\nCOMPOUNDRULE PQ\n";
  }

  private static String flagAff(String extra) {
    return "SET UTF-8\n" + extra + "\nCOMPOUNDFLAG X\n";
  }

  private static void report(String label, Hunspell h, String... words) {
    StringBuilder sb = new StringBuilder("OBSERVED " + label + ":");
    for (String w : words) {
      sb.append(' ').append(w).append('=').append(h.spell(w));
    }
    System.out.println(sb);
  }

  /** KEEPCASE on the first part: Sunrise / SUNRISE must be rejected. */
  public void testKeepCaseFirstPart() throws Exception {
    Hunspell flag = load(flagAff("KEEPCASE K"), "2\nsun/XK\nrise/X\n");
    Hunspell rule = load(ruleAff("KEEPCASE K"), "2\nsun/PK\nrise/Q\n");
    report("keepcase-flag", flag, "sun", "Sun", "sunrise", "Sunrise", "SUNRISE");
    report("keepcase-rule", rule, "sun", "Sun", "sunrise", "Sunrise", "SUNRISE");

    // controls: the COMPOUNDFLAG twin and the positive form
    assertTrue(flag.spell("sunrise"));
    assertFalse("COMPOUNDFLAG twin rejects Sunrise", flag.spell("Sunrise"));
    assertFalse("COMPOUNDFLAG twin rejects SUNRISE", flag.spell("SUNRISE"));
    assertFalse("KEEPCASE simple word Sun rejected", rule.spell("Sun"));
    assertTrue("rule compound sunrise accepted", rule.spell("sunrise"));

    // the claim
    assertFalse("COMPOUNDRULE compound Sunrise with KEEPCASE first part sun", rule.spell("Sunrise"));
    assertFalse("COMPOUNDRULE compound SUNRISE with KEEPCASE first part sun", rule.spell("SUNRISE"));
  }

  /** FORCEUCASE on the last part: lower-case sunrise must be rejected, Sunrise accepted. */
  public void testForceUCaseLastPart() throws Exception {
    Hunspell flag = load(flagAff("FORCEUCASE U"), "2\nsun/X\nrise/XU\n");
    Hunspell rule = load(ruleAff("FORCEUCASE U"), "2\nsun/P\nrise/QU\n");
    report("forceucase-flag", flag, "rise", "sunrise", "Sunrise", "SUNRISE");
    report("forceucase-rule", rule, "rise", "sunrise", "Sunrise", "SUNRISE");

    assertTrue(flag.spell("Sunrise"));
    assertFalse("COMPOUNDFLAG twin rejects sunrise", flag.spell("sunrise"));
    assertTrue("rule compound Sunrise accepted", rule.spell("Sunrise"));

    assertFalse("COMPOUNDRULE compound sunrise with FORCEUCASE last part rise", rule.spell("sunrise"));
  }

  /** NEEDAFFIX (virtual stem) as the first part: sunrise must be rejected. */
  public void testNeedAffixFirstPart() throws Exception {
    String sfx = "NEEDAFFIX N\nSFX S Y 1\nSFX S 0 s .";
    Hunspell flag = load(flagAff(sfx), "2\nsun/XNS\nrise/X\n");
    Hunspell rule = load(ruleAff(sfx), "2\nsun/PNS\nrise/Q\n");
    // Lucene's own last-part check (Stemmer.doStem) already skips a NEEDAFFIX entry:
    Hunspell ruleLast = load(ruleAff(sfx), "2\nsun/P\nrise/QNS\n");
    report("needaffix-flag", flag, "sun", "suns", "sunrise");
    report("needaffix-rule-first", rule, "sun", "suns", "sunrise");
    report("needaffix-rule-last", ruleLast, "rise", "rises", "sunrise");

    assertFalse("virtual stem alone rejected", rule.spell("sun"));
    assertTrue("virtual stem with its affix accepted", rule.spell("suns"));
    assertFalse("COMPOUNDFLAG twin rejects sunrise", flag.spell("sunrise"));
    assertFalse("COMPOUNDRULE with NEEDAFFIX last part rejects sunrise", ruleLast.spell("sunrise"));

    assertFalse("COMPOUNDRULE compound sunrise with NEEDAFFIX first part sun", rule.spell("sunrise"));
  }

  /** FORBIDDENWORD entry as the first part: sunrise must be rejected. */
  public void testForbiddenWordFirstPart() throws Exception {
    Hunspell flag = load(flagAff("FORBIDDENWORD F"), "2\nsun/XF\nrise/X\n");
    Hunspell rule = load(ruleAff("FORBIDDENWORD F"), "2\nsun/PF\nrise/Q\n");
    // exploratory: forbidden last part (not part of the claim; printed only)
    Hunspell ruleLast = load(ruleAff("FORBIDDENWORD F"), "2\nsun/P\nrise/QF\n");
    report("forbidden-flag", flag, "sun", "sunrise");
    report("forbidden-rule-first", rule, "sun", "sunrise");
    report("forbidden-rule-last", ruleLast, "rise", "sunrise");

    assertFalse("forbidden word alone rejected", rule.spell("sun"));
    assertFalse("COMPOUNDFLAG twin rejects sunrise", flag.spell("sunrise"));

    assertFalse("COMPOUNDRULE compound sunrise with FORBIDDENWORD first part sun", rule.spell("sunrise"));
  }

  /** Middle part, three-part rule PQ*: NEEDAFFIX on a middle part. */
  public void testNeedAffixMiddlePart() throws Exception {
    String aff = "SET UTF-8\nNEEDAFFIX N\nCOMPOUNDRULE 1\nCOMPOUNDRULE PQ*\n";
    Hunspell rule = load(aff, "3\nsun/P\nrise/QN\nset/Q\n");
    report("needaffix-rule-middle", rule, "sunrise", "sunset", "sunriseset", "sunsetrise");
    assertTrue("control sunset", rule.spell("sunset"));
    assertFalse("control: NEEDAFFIX last part rejected", rule.spell("sunsetrise"));
    assertFalse("COMPOUNDRULE compound sunriseset with NEEDAFFIX middle part rise",
        rule.spell("sunriseset"));
  }
}
