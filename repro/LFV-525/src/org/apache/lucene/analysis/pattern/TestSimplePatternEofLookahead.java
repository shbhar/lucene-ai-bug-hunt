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
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * SimplePatternTokenizer / SimplePatternSplitTokenizer: when a candidate match starting at some
 * position runs to the end of the input without ever reaching an accept state, the scan stops
 * instead of retrying from the next character (the "false alarm" path), so later tokens (or later
 * separators) are lost.
 */
public class TestSimplePatternEofLookahead extends BaseTokenStreamTestCase {

  private static Tokenizer tokenizer(String pattern) {
    Map<String, String> args = new HashMap<>();
    args.put("pattern", pattern);
    return new SimplePatternTokenizerFactory(args).create();
  }

  private static Tokenizer splitTokenizer(String pattern) {
    Map<String, String> args = new HashMap<>();
    args.put("pattern", pattern);
    return new SimplePatternSplitTokenizerFactory(args).create();
  }

  /** Tokens are words or double-quoted phrases; the last quote of the text is never closed. */
  public void testTokenizerUnclosedQuoteDropsRestOfText() throws Exception {
    Tokenizer t = tokenizer("\\\"[^\\\"]*\\\"|[a-z]+");
    // control: with the quote closed, every word is found
    t.setReader(new StringReader("say \"hello\" world"));
    assertTokenStreamContents(
        t, new String[] {"say", "\"hello\"", "world"}, new int[] {0, 4, 12}, new int[] {3, 11, 17}, 17);
    // at '"' no match exists (no closing quote), so per the javadoc ("the longest match at a given
    // start point will be the next token") scanning continues at 'h': hello and world are tokens
    t.setReader(new StringReader("say \"hello world"));
    assertTokenStreamContents(
        t, new String[] {"say", "hello", "world"}, new int[] {0, 5, 11}, new int[] {3, 10, 16}, 16);
  }

  /** Same root cause with an apostrophe-quoted alternative: every word after "don't" is lost. */
  public void testTokenizerApostrophe() throws Exception {
    Tokenizer t = tokenizer("'[^']*'|[a-z]+");
    t.setReader(new StringReader("don't stop me"));
    assertTokenStreamContents(
        t,
        new String[] {"don", "t", "stop", "me"},
        new int[] {0, 4, 6, 11},
        new int[] {3, 5, 10, 13},
        13);
  }

  /** Separators are tags or runs of spaces; an unclosed '<' swallows the following separators. */
  public void testSplitTokenizerUnclosedTagMergesTokens() throws Exception {
    Tokenizer t = splitTokenizer("\\<[^\\>]*\\>|[ ]+");
    // control: a closed tag is a separator
    t.setReader(new StringReader("x <b> y"));
    assertTokenStreamContents(t, new String[] {"x", "y"}, new int[] {0, 6}, new int[] {1, 7}, 7);
    // no separator starts at '<' (no '>'), so the space after it is the next separator
    t.setReader(new StringReader("x < y"));
    assertTokenStreamContents(
        t, new String[] {"x", "<", "y"}, new int[] {0, 2, 4}, new int[] {1, 3, 5}, 5);
  }
}
