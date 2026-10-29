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

public class TestSimplePatternSurrogateRetry extends BaseTokenStreamTestCase {

  private static final String E = new String(Character.toChars(0x1F525));

  private static Automaton dfa(String regexp) {
    return Operations.determinize(
        new RegExp(regexp).toAutomaton(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
  }

  public void testTokenizer() throws Exception {
    Tokenizer t = new SimplePatternTokenizer(dfa(E + "[0-9]+|[^" + E + " ]+"));
    t.setReader(new StringReader(E + " x"));
    // expected [x] [3,4); actual [U+DD25 [1,2), x]: the retry after E starts at its low surrogate
    assertTokenStreamContents(t, new String[] {"x"}, new int[] {3}, new int[] {4}, 4);
  }

  public void testSplitTokenizer() throws Exception {
    Tokenizer t = new SimplePatternSplitTokenizer(dfa(E + "[0-9]+|[^" + E + "a-z]+"));
    t.setReader(new StringReader(E + "b"));
    // expected [Eb] [0,3); actual [U+D83D [0,1), b]: the retry after E starts at its low surrogate
    assertTokenStreamContents(t, new String[] {E + "b"}, new int[] {0}, new int[] {3}, 3);
  }
}
