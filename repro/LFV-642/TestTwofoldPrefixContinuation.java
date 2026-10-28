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
package org.apache.lucene.verify.hunspell;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.hunspell.Dictionary;
import org.apache.lucene.analysis.hunspell.Hunspell;
import java.io.StringReader;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.hunspell.HunspellStemFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Twofold affix stripping: the outer affix may only follow an inner affix whose continuation class
 * contains the outer affix's flag (hunspell(5), "Twofold suffix stripping"; with COMPLEXPREFIXES
 * the same applies to two prefixes). Public API only (separate package).
 */
public class TestTwofoldPrefixContinuation extends LuceneTestCase {

  private static Dictionary load(String aff, String dic) throws Exception {
    try (InputStream a = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream d = new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))) {
      return new Dictionary(new ByteBuffersDirectory(), "verify", a, d);
    }
  }

  private static List<String> stems(Dictionary dict, String word) {
    return new Hunspell(dict).getRoots(word);
  }

  /** Tokens emitted by the indexing-time HunspellStemFilter for a single input word. */
  private static List<String> stemFilterTokens(Dictionary dict, String word) throws Exception {
    List<String> out = new ArrayList<>();
    Tokenizer tok = new WhitespaceTokenizer();
    tok.setReader(new StringReader(word));
    try (TokenStream ts = new HunspellStemFilter(tok, dict)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  // COMPLEXPREFIXES: inner prefixes "un" (no continuation) and "dis/A"; outer prefix "re" has flag A.
  private static final String COMPLEX_AFF =
      "SET UTF-8\n"
          + "COMPLEXPREFIXES\n"
          + "\n"
          + "PFX A Y 1\n"
          + "PFX A 0 re .\n"
          + "\n"
          + "PFX C Y 2\n"
          + "PFX C 0 un .\n"
          + "PFX C 0 dis/A .\n";

  // Same, but the continuation-less inner prefix "un" is in its own class D.
  private static final String COMPLEX_AFF_SEPARATE_CLASS =
      "SET UTF-8\n"
          + "COMPLEXPREFIXES\n"
          + "\n"
          + "PFX A Y 1\n"
          + "PFX A 0 re .\n"
          + "\n"
          + "PFX C Y 1\n"
          + "PFX C 0 dis/A .\n"
          + "\n"
          + "PFX D Y 1\n"
          + "PFX D 0 un .\n";

  // The suffix mirror image without COMPLEXPREFIXES: inner "x" (no continuation), "y/A"; outer "s".
  private static final String SUFFIX_AFF =
      "SET UTF-8\n"
          + "\n"
          + "SFX A Y 1\n"
          + "SFX A 0 s .\n"
          + "\n"
          + "SFX C Y 2\n"
          + "SFX C 0 x .\n"
          + "SFX C 0 y/A .\n";

  /** Controls that must hold both with and without the bug. */
  public void testControls() throws Exception {
    Hunspell h = new Hunspell(load(COMPLEX_AFF, "1\nfoo/C\n"));
    assertTrue(h.spell("foo"));
    assertTrue(h.spell("unfoo"));
    assertTrue(h.spell("disfoo"));
    assertTrue("re is a continuation of dis/A", h.spell("redisfoo"));
    assertFalse("foo has no flag A", h.spell("refoo"));

    Hunspell s = new Hunspell(load(SUFFIX_AFF, "1\nfoo/C\n"));
    assertTrue(s.spell("foox"));
    assertTrue(s.spell("fooy"));
    assertTrue("s is a continuation of y/A", s.spell("fooys"));
    assertFalse("suffix mirror: x has no continuation class", s.spell("fooxs"));
  }

  /** The claim: the second prefix "re" is accepted after "un", whose rule does not carry A. */
  public void testSecondPrefixAfterRuleWithoutContinuation() throws Exception {
    Dictionary dict = load(COMPLEX_AFF, "1\nfoo/C\n");
    Hunspell h = new Hunspell(dict);
    List<String> st = stems(dict, "reunfoo");
    assertFalse(
        "reunfoo: un has no continuation class, so re cannot precede it; stems=" + st,
        h.spell("reunfoo"));
    assertEquals("stems of reunfoo", List.of(), st);
  }

  /** Indexing-time effect: HunspellStemFilter keeps an unknown word as is (no stem found). */
  public void testStemFilterConflatesReunfooWithFoo() throws Exception {
    Dictionary dict = load(COMPLEX_AFF, "1\nfoo/C\n");
    assertEquals("control", List.of("foo"), stemFilterTokens(dict, "redisfoo"));
    assertEquals(List.of("reunfoo"), stemFilterTokens(dict, "reunfoo"));
  }

  /** Same with the continuation-less inner prefix in a different class from dis/A. */
  public void testSecondPrefixAfterRuleOfOtherClass() throws Exception {
    Dictionary dict = load(COMPLEX_AFF_SEPARATE_CLASS, "1\nfoo/CD\n");
    Hunspell h = new Hunspell(dict);
    assertTrue(h.spell("unfoo"));
    assertTrue(h.spell("redisfoo"));
    List<String> st = stems(dict, "reunfoo");
    assertFalse(
        "reunfoo (un in class D, no continuation class); stems=" + st, h.spell("reunfoo"));
  }
}
