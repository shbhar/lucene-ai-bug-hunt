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

import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDeterminizeRangeEndingAtMaxValue extends LuceneTestCase {

  public void testRangeEndingAtMaxValue() {
    Automaton hi =
        Operations.concatenate(
            List.of(Automata.makeChar(0), Automata.makeCharRange(11, Integer.MAX_VALUE)));
    Automaton lo =
        Operations.concatenate(List.of(Automata.makeChar(0), Automata.makeCharRange(0, 10)));
    // expected: a DFA accepting "\0\5"; actual: AssertionError (-ea) / AIOOBE (-da) in StateSet.decr
    Automaton d =
        Operations.determinize(
            Operations.union(List.of(hi, lo)), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
    assertTrue(Operations.run(d, "\u0000\u0005"));
  }
}
