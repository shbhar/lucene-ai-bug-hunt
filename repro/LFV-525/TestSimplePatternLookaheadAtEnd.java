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
 * When the lookahead from a start point reaches the end of the input without ever matching, the
 * simple pattern tokenizers stop instead of trying the next start point, as they do when the
 * lookahead fails before the end.
 */
public class TestSimplePatternLookaheadAtEnd extends BaseTokenStreamTestCase {

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

  /** Control: the lookahead "ab" from 'a' dies at 'x', so the scan retries at 'b'. */
  public void testControlLookaheadFailsBeforeEnd() throws Exception {
    Tokenizer t = tokenizer("abc|b");
    t.setReader(new StringReader("abx"));
    assertTokenStreamContents(t, new String[] {"b"}, new int[] {1}, new int[] {2}, 3);
  }

  /** The same lookahead reaches the end of the input instead: "b" is lost. */
  public void testLookaheadReachesEnd() throws Exception {
    Tokenizer t = tokenizer("abc|b");
    t.setReader(new StringReader("ab"));
    assertTokenStreamContents(t, new String[] {"b"}, new int[] {1}, new int[] {2}, 2);
  }

  /** A quoted phrase or a word: an unclosed quote loses every later word. */
  public void testUnclosedQuote() throws Exception {
    Tokenizer t = tokenizer("\\\"[^\\\"]*\\\"|[a-z]+");
    t.setReader(new StringReader("say \"hello\" world"));
    assertTokenStreamContents(
        t,
        new String[] {"say", "\"hello\"", "world"},
        new int[] {0, 4, 12},
        new int[] {3, 11, 17},
        17);
    t.setReader(new StringReader("say \"hello world"));
    assertTokenStreamContents(
        t, new String[] {"say", "hello", "world"}, new int[] {0, 5, 11}, new int[] {3, 10, 16}, 16);
  }

  /** The same with single quotes: an apostrophe is an unclosed quote. */
  public void testApostrophe() throws Exception {
    Tokenizer t = tokenizer("'[^']*'|[a-z]+");
    t.setReader(new StringReader("don't stop me"));
    assertTokenStreamContents(
        t,
        new String[] {"don", "t", "stop", "me"},
        new int[] {0, 4, 6, 11},
        new int[] {3, 5, 10, 13},
        13);
  }

  /** Split tokenizer, separators are tags or spaces: an unclosed {@code <} hides later spaces. */
  public void testSplitUnclosedTag() throws Exception {
    Tokenizer t = splitTokenizer("\\<[^\\>]*\\>|[ ]+");
    t.setReader(new StringReader("x <b> y"));
    assertTokenStreamContents(t, new String[] {"x", "y"}, new int[] {0, 6}, new int[] {1, 7}, 7);
    t.setReader(new StringReader("x < y"));
    assertTokenStreamContents(
        t, new String[] {"x", "<", "y"}, new int[] {0, 2, 4}, new int[] {1, 3, 5}, 5);
  }
}
