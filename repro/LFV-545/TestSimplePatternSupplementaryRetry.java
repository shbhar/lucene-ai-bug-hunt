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
package org.apache.lucene.analysis.pattern;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * The simple pattern tokenizers step their automaton over code points. When a match attempt that
 * started at a supplementary character (two UTF-16 units) fails after reading further, they retry
 * one UTF-16 unit later, inside the surrogate pair, instead of at the next code point.
 */
public class TestSimplePatternSupplementaryRetry extends BaseTokenStreamTestCase {

  /** U+1F525, two UTF-16 units: high surrogate U+D83D, low surrogate U+DD25. */
  private static final String FIRE = new String(Character.toChars(0x1F525));

  /** A token is FIRE followed by digits, or a run of characters other than FIRE and space. */
  private static final String WORDS = FIRE + "[0-9]+|[^" + FIRE + " ]+";

  /** A separator is a run of characters other than a-z and FIRE, or FIRE followed by '!'. */
  private static final String SEPARATORS = "[^a-z" + FIRE + "]+|" + FIRE + "!";

  private static Tokenizer tokenizer(String pattern, String text) {
    Map<String, String> args = new HashMap<>();
    args.put("pattern", pattern);
    Tokenizer t = new SimplePatternTokenizerFactory(args).create();
    t.setReader(new StringReader(text));
    return t;
  }

  private static Tokenizer splitTokenizer(String pattern, String text) {
    Map<String, String> args = new HashMap<>();
    args.put("pattern", pattern);
    Tokenizer t = new SimplePatternSplitTokenizerFactory(args).create();
    t.setReader(new StringReader(text));
    return t;
  }

  /** Control: when the FIRE alternative matches, both tokenizers are right. */
  public void testControlFireAlternativeMatches() throws Exception {
    assertTokenStreamContents(
        tokenizer(WORDS, FIRE + "5 hot deal"),
        new String[] {FIRE + "5", "hot", "deal"},
        new int[] {0, 4, 8},
        new int[] {3, 7, 12},
        12);
    assertTokenStreamContents(
        splitTokenizer(SEPARATORS, "so" + FIRE + "!hot now"),
        new String[] {"so", "hot", "now"},
        new int[] {0, 5, 9},
        new int[] {2, 8, 12},
        12);
  }

  /** Control: PatternTokenizer (java.util.regex) with the same regexes keeps the pair intact. */
  public void testControlPatternTokenizer() throws Exception {
    Tokenizer t = new PatternTokenizer(Pattern.compile(WORDS), 0);
    t.setReader(new StringReader(FIRE + "hot deal"));
    assertTokenStreamContents(
        t, new String[] {"hot", "deal"}, new int[] {2, 6}, new int[] {5, 10}, 10);
    Tokenizer split = new PatternTokenizer(Pattern.compile(SEPARATORS), -1);
    split.setReader(new StringReader("so" + FIRE + "hot now"));
    assertTokenStreamContents(
        split, new String[] {"so" + FIRE + "hot", "now"}, new int[] {0, 8}, new int[] {7, 11}, 11);
  }

  /** An alternative that matches nowhere in the text must not change the tokens. */
  public void testUnusedAlternativeChangesTokens() throws Exception {
    String text = FIRE + "hot deal";
    // without the FIRE alternative: "hot" [2,5) and "deal" [6,10) (this assert passes)
    assertTokenStreamContents(
        tokenizer("[^" + FIRE + " ]+", text),
        new String[] {"hot", "deal"},
        new int[] {2, 6},
        new int[] {5, 10},
        10);
    // with FIRE[0-9]+ added, which matches nowhere in this text, "hot" must stay the same
    assertTokenStreamContents(
        tokenizer(WORDS, text),
        new String[] {"hot", "deal"},
        new int[] {2, 6},
        new int[] {5, 10},
        10);
  }

  /** FIRE followed by a space: no token may consist of half of FIRE. */
  public void testNoTokenFromHalfAnEmoji() throws Exception {
    assertTokenStreamContents(
        tokenizer(WORDS, FIRE + " x"), new String[] {"x"}, new int[] {3}, new int[] {4}, 4);
  }

  /** Split tokenizer: FIRE not followed by '!' is no separator, so the word stays whole. */
  public void testSplitKeepsWordWithEmoji() throws Exception {
    String text = "so" + FIRE + "hot now";
    // without the FIRE! alternative (this assert passes)
    assertTokenStreamContents(
        splitTokenizer("[^a-z" + FIRE + "]+", text),
        new String[] {"so" + FIRE + "hot", "now"},
        new int[] {0, 8},
        new int[] {7, 11},
        11);
    // with FIRE! added, which occurs nowhere in this text, the tokens must stay the same
    assertTokenStreamContents(
        splitTokenizer(SEPARATORS, text),
        new String[] {"so" + FIRE + "hot", "now"},
        new int[] {0, 8},
        new int[] {7, 11},
        11);
  }
}
