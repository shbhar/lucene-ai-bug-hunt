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
package org.apache.lucene.util.automaton;

import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Automata#makeDecimalInterval documents an automaton over "decimal (base 10) non-negative integers
 * in the given interval" and rejects only min &gt; max and too-few digits. A negative min is accepted
 * and the '-' of Integer.toString(min) is then treated as a digit character, so the result accepts
 * out-of-interval strings ("12", "-5") and rejects in-interval ones ("0", "1").
 */
public class TestMakeDecimalIntervalNegativeMin extends LuceneTestCase {

  public void testNegativeMin() {
    Automaton a;
    try {
      a = Automata.makeDecimalInterval(-1, 5, 0);
    } catch (IllegalArgumentException rejected) {
      return; // rejecting a negative min up front is a valid fix
    }
    CharacterRunAutomaton run = new CharacterRunAutomaton(Operations.determinize(a, 10000));
    StringBuilder wrong = new StringBuilder();
    // non-negative integers in [-1, 5] are 0..5 (leading zeros allowed when digits <= 0)
    for (String s : new String[] {"0", "1", "3", "5", "05"}) {
      if (run.run(s) == false) wrong.append(" rejects in-interval \"").append(s).append('"');
    }
    for (String s : new String[] {"6", "12", "-5", "-1"}) {
      if (run.run(s)) wrong.append(" accepts out-of-interval \"").append(s).append('"');
    }
    assertEquals("makeDecimalInterval(-1, 5, 0):" + wrong, "", wrong.toString());
  }

  /** Control: the same check for min = 0 passes. */
  public void testZeroMinControl() {
    CharacterRunAutomaton run =
        new CharacterRunAutomaton(Operations.determinize(Automata.makeDecimalInterval(0, 5, 0), 10000));
    for (String s : new String[] {"0", "1", "3", "5", "05"}) assertTrue(s, run.run(s));
    for (String s : new String[] {"6", "12", "-5", "-1"}) assertFalse(s, run.run(s));
  }
}
