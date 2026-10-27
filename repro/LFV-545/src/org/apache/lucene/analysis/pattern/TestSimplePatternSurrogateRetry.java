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
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;

/**
 * SimplePatternTokenizer / SimplePatternSplitTokenizer run the automaton over code points, but
 * when a candidate match that started at a supplementary character fails, they push back all but
 * the first UTF-16 code unit, so the next attempt starts at the low surrogate: tokens (or token
 * boundaries) can then split a surrogate pair.
 */
public class TestSimplePatternSurrogateRetry extends BaseTokenStreamTestCase {

  private static final String FIRE = new String(Character.toChars(0x1F525)); // a supplementary char

  private static Automaton dfa(String regexp) {
    return Operations.determinize(new RegExp(regexp).toAutomaton(), 10000);
  }

  /** Tokens: FIRE followed by letters, or runs of characters other than FIRE and space. */
  public void testTokenizerNoLoneSurrogateToken() throws Exception {
    Tokenizer t = new SimplePatternTokenizer(dfa(FIRE + "[a-z]+|[^" + FIRE + " ]+"));
    // control: FIRE followed by letters is one token
    t.setReader(new StringReader(FIRE + "hot x"));
    assertTokenStreamContents(
        t, new String[] {FIRE + "hot", "x"}, new int[] {0, 6}, new int[] {5, 7}, 7);
    // no token starts at FIRE (a space follows); the next token is "x" at offset 3
    t.setReader(new StringReader(FIRE + " x"));
    assertTokenStreamContents(t, new String[] {"x"}, new int[] {3}, new int[] {4}, 4);
  }

  /** Separators: FIRE followed by 'a', or runs of characters other than FIRE, 'a' and space. */
  public void testSplitTokenizerKeepsSurrogatePair() throws Exception {
    Tokenizer t = new SimplePatternSplitTokenizer(dfa(FIRE + "a|[^" + FIRE + "a ]+"));
    // no separator starts at FIRE ('b' follows), so FIRE is the token and 'b' the separator
    t.setReader(new StringReader(FIRE + "b"));
    assertTokenStreamContents(t, new String[] {FIRE}, new int[] {0}, new int[] {2}, 3);
  }
}
