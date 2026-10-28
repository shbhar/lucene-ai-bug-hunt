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
 * Verifier (intended-behaviour check): is the failure reachable with an unmodified upstream
 * conformance dictionary, does the conformance list itself hide it, and does the documented
 * COMPOUNDWORDMAX knob avoid it? Public API only (Dictionary, Hunspell).
 */
public class TestSimplifiedCompoundReachability extends LuceneTestCase {

  private static Hunspell load(String aff, String dic) throws Exception {
    try (InputStream a = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream d = new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))) {
      return new Hunspell(new Dictionary(a, List.of(d), false, SortingStrategy.inMemory()));
    }
  }

  /** Hunspell 1.7.2 tests/checkcompoundpattern4.aff, verbatim (Telugu sandhi example). */
  private static final String AFF4 =
      "# sandhi in Telugu writing system, based on the Kiran Chittella's example\n"
          + "\n"
          + "COMPOUNDFLAG x\n"
          + "COMPOUNDMIN 1\n"
          + "CHECKCOMPOUNDPATTERN 2\n"
          + "CHECKCOMPOUNDPATTERN a/A u/A O\n"
          + "CHECKCOMPOUNDPATTERN u/B u/B u\n"
          + "\n";

  /** Hunspell 1.7.2 tests/checkcompoundpattern4.dic, verbatim. */
  private static final String DIC4 = "4\nsUrya/Ax\nudayaM/Ax\npEru/Bx\nunna/Bx\n\n";

  /** Lucene's own fixture checkcompoundpattern2.aff/.dic content. */
  private static final String AFF2 =
      "COMPOUNDFLAG A\n"
          + "CHECKCOMPOUNDPATTERN 2\n"
          + "CHECKCOMPOUNDPATTERN o b z\n"
          + "CHECKCOMPOUNDPATTERN oo ba u\n"
          + "COMPOUNDMIN 1\n";

  private static final String DIC2 = "2\nfoo/A\nbar/A\n";

  /** Control: the fixture's own .good/.wrong lists pass on stock Lucene. */
  public void testFixture4OwnListsPass() throws Exception {
    Hunspell h = load(AFF4, DIC4);
    assertTrue(h.spell("sUryOdayaM"));
    assertTrue(h.spell("pErunna"));
    assertFalse(h.spell("sUryaudayaM"));
    assertFalse(h.spell("pEruunna"));
  }

  private static String probe(Hunspell h, String w) {
    try {
      return Boolean.toString(h.spell(w));
    } catch (StackOverflowError e) {
      return "StackOverflowError";
    }
  }

  /**
   * Ordinary 3-part words over the unmodified upstream dictionary: spell() must return a boolean.
   * The value is not asserted (C Hunspell 1.7.2 itself is position-dependent here).
   */
  public void testFixture4ThreePartWordsReturnBoolean() throws Exception {
    Hunspell h = load(AFF4, DIC4);
    StringBuilder sb = new StringBuilder();
    boolean overflow = false;
    for (String w : new String[] {"pErupErupEru", "unnapEruunna", "sUryapEruunna", "pErunnapEru"}) {
      String r = probe(h, w);
      sb.append(w).append('=').append(r).append(' ');
      overflow |= r.equals("StackOverflowError");
    }
    System.out.println("V3 fixture4 3-part: " + sb);
    assertFalse("spell() threw StackOverflowError: " + sb, overflow);
  }

  /** Documented knob: COMPOUNDWORDMAX 3. Does it bound the recursion? */
  public void testFixture4WithCompoundWordMax3() throws Exception {
    Hunspell h = load(AFF4.replace("COMPOUNDMIN 1\n", "COMPOUNDMIN 1\nCOMPOUNDWORDMAX 3\n"), DIC4);
    StringBuilder sb = new StringBuilder();
    boolean overflow = false;
    for (String w : new String[] {"pErupErupEru", "unnapEruunna", "sUryapEruunna"}) {
      String r = probe(h, w);
      sb.append(w).append('=').append(r).append(' ');
      overflow |= r.equals("StackOverflowError");
    }
    System.out.println("V3 fixture4 COMPOUNDWORDMAX 3: " + sb);
    assertFalse("spell() threw StackOverflowError even with COMPOUNDWORDMAX 3: " + sb, overflow);
  }

  /** COMPOUNDWORDMAX 3 does not cure the false negative (3 parts are allowed). */
  public void testPattern2WithCompoundWordMax3StillRejects() throws Exception {
    Hunspell h = load(AFF2.replace("COMPOUNDMIN 1\n", "COMPOUNDMIN 1\nCOMPOUNDWORDMAX 3\n"), DIC2);
    assertTrue("control: barbarfoo is a 3-part compound", h.spell("barbarfoo"));
    assertTrue("barfozar = bar + foo|bar simplified, 3 parts", h.spell("barfozar"));
  }

  /** suggest() on the unmodified upstream dictionary. */
  public void testFixture4Suggest() throws Exception {
    Hunspell h = load(AFF4, DIC4);
    String r;
    try {
      r = String.valueOf(h.suggest("pErupErupEr"));
    } catch (StackOverflowError e) {
      r = "StackOverflowError";
    }
    System.out.println("V3 fixture4 suggest(pErupErupEr) = " + r);
    assertFalse("suggest() threw StackOverflowError", r.equals("StackOverflowError"));
  }
}
