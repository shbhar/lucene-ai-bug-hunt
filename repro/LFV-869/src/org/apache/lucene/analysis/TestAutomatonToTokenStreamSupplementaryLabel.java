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
package org.apache.lucene.analysis;

import java.io.IOException;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;

/**
 * AutomatonToTokenStream releases every edge of the automaton as a token, but the token text is
 * {@code (char) label}: a label above U+FFFF loses its high bits and becomes a different character.
 */
public class TestAutomatonToTokenStreamSupplementaryLabel extends BaseTokenStreamTestCase {

  /** U+10400 DESERET CAPITAL LETTER LONG I, a letter outside the BMP (two UTF-16 chars). */
  private static final String LONG_I = new String(Character.toChars(0x10400));

  public void testSupplementaryLabel() throws IOException {
    // Automata.makeString labels its transitions with code points: one transition, label 0x10400
    Automaton a = Automata.makeString(LONG_I);
    TokenStream ts = AutomatonToTokenStream.toTokenStream(a);
    // expected: one token whose text is the label; actual: U+0400 (CYRILLIC CAPITAL LETTER IE WITH
    // GRAVE), the label with its high bits cut off
    assertTokenStreamContents(
        ts, new String[] {LONG_I}, new int[] {0}, new int[] {1}, new int[] {1}, new int[] {1}, 1);
  }

  public void testSupplementaryLabelFromRegExp() throws IOException {
    Automaton a = Operations.determinize(new RegExp("a" + LONG_I).toAutomaton(), 10_000);
    TokenStream ts = AutomatonToTokenStream.toTokenStream(a);
    assertTokenStreamContents(
        ts,
        new String[] {"a", LONG_I},
        new int[] {0, 1},
        new int[] {1, 2},
        new int[] {1, 1},
        new int[] {1, 1},
        2);
  }

  public void testRoundTripKeepsTheLanguage() throws IOException {
    Automaton a = Automata.makeString("a" + LONG_I);
    TokenStreamToAutomaton tsa = new TokenStreamToAutomaton();
    tsa.setUnicodeArcs(true);
    Automaton back =
        Operations.determinize(
            tsa.toAutomaton(AutomatonToTokenStream.toTokenStream(a)), 10_000);
    String withSep = "a" + (char) TokenStreamToAutomaton.POS_SEP;
    // actual: the round trip accepts "a" POS_SEP U+0400 and rejects "a" POS_SEP U+10400
    assertFalse(Operations.run(back, withSep + "\u0400"));
    assertTrue(Operations.run(back, withSep + LONG_I));
  }
}
