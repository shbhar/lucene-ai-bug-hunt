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
package hunspellv1;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.hunspell.Dictionary;
import org.apache.lucene.analysis.hunspell.Hunspell;
import org.apache.lucene.analysis.hunspell.HunspellStemFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * hunspell(5), affix condition: "Characters in braces sign an arbitrary character from the
 * character subset. Dash hasn't got special meaning". So [x-z] is the set {x, -, z}.
 *
 * <p>Only the public API (Dictionary, Hunspell, HunspellStemFilter) is used, from a different
 * package.
 * Each rule is chosen so that the bracket with a dash covers a character of the strip.
 */
public class TestDashInStripCondition extends LuceneTestCase {

  private Dictionary load(String aff, String dic) throws Exception {
    try (Directory dir = newDirectory();
        InputStream a = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream d = new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))) {
      return new Dictionary(dir, "dict", a, d);
    }
  }

  private Hunspell speller(String aff, String dic) throws Exception {
    return new Hunspell(load(aff, dic));
  }

  /** Suffix, strip "y", condition [x-z] = {x,-,z}: y is not in the set, so no "happies". */
  public void testSuffixRangeOverStripOverAccepts() throws Exception {
    Hunspell h = speller("SET UTF-8\nSFX A Y 1\nSFX A y ies [x-z]\n", "1\nhappy/A\n");
    assertTrue(h.spell("happy"));
    assertFalse("happies", h.spell("happies"));
  }

  /** Suffix, strip "y", condition [^x-z]: y is not in {x,-,z}, so "happied" is correct. */
  public void testSuffixNegatedRangeOverStripUnderAccepts() throws Exception {
    Hunspell h = speller("SET UTF-8\nSFX B Y 1\nSFX B y ied [^x-z]\n", "1\nhappy/B\n");
    assertTrue("happied", h.spell("happied"));
  }

  /** Prefix, strip "y", condition [x-z]: y is not in {x,-,z}, so no "zes" from "yes". */
  public void testPrefixRangeOverStripOverAccepts() throws Exception {
    Hunspell h = speller("SET UTF-8\nPFX C Y 1\nPFX C y z [x-z]\n", "1\nyes/C\n");
    assertTrue(h.spell("yes"));
    assertFalse("zes", h.spell("zes"));
  }

  /** Two-character strip "ey", condition [d-f]y: e is not in {d,-,f}, so no "kow" from "key". */
  public void testTwoCharStripRangeOverAccepts() throws Exception {
    Hunspell h = speller("SET UTF-8\nSFX D Y 1\nSFX D ey ow [d-f]y\n", "1\nkey/D\n");
    assertTrue(h.spell("key"));
    assertFalse("kow", h.spell("kow"));
  }

  /**
   * Strip "a", condition [z-a] = {z,-,a}: a is in the set, so "pizzo" is correct. A regex engine
   * that reads the dash as a range rejects z-a as an illegal (reversed) range.
   */
  public void testReversedRangeOverStripUnderAccepts() throws Exception {
    Hunspell h = speller("SET UTF-8\nSFX E Y 1\nSFX E a o [z-a]\n", "1\npizza/E\n");
    assertTrue("pizzo", h.spell("pizzo"));
  }

  /**
   * HunspellStemFilter must not stem "happies" to "happy"; with no stem it returns the token
   * unchanged.
   */
  public void testStemFilterOverStems() throws Exception {
    Dictionary d = load("SET UTF-8\nSFX A Y 1\nSFX A y ies [x-z]\n", "1\nhappy/A\n");
    WhitespaceTokenizer tok = new WhitespaceTokenizer();
    tok.setReader(new StringReader("happies"));
    List<String> out = new ArrayList<>();
    try (TokenStream ts = new HunspellStemFilter(tok, d)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    assertEquals(List.of("happies"), out);
  }

  /** Control (passes): same [x-z] condition, but on the stem (empty strip): read literally. */
  public void testControlRangeOnStemIsLiteral() throws Exception {
    Hunspell h = speller("SET UTF-8\nSFX F Y 1\nSFX F 0 s [x-z]\n", "2\nhappy/F\nfez/F\n");
    assertFalse("happys", h.spell("happys"));
    assertTrue("fezs", h.spell("fezs"));
  }

  /** Control (passes): the same strip check with the dash removed ([xz]) rejects "happies". */
  public void testControlNoDashOverStrip() throws Exception {
    Hunspell h = speller("SET UTF-8\nSFX G Y 1\nSFX G y ies [xz]\n", "1\nhappy/G\n");
    assertFalse("happies", h.spell("happies"));
  }
}
