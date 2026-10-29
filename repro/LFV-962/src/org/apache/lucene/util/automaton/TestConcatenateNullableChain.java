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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Operations.concatenate javadoc: "Complexity: linear in total number of states." Concatenating n
 * automata that accept the empty string links every accept state to all following pieces, so the
 * result has about n^2/2 transitions.
 */
public class TestConcatenateNullableChain extends LuceneTestCase {
  public void testConcatenateOfAnyStrings() {
    int n = 1000;
    List<Automaton> list = new ArrayList<>();
    long inStates = 0, inTransitions = 0;
    for (int i = 0; i < n; i++) {
      Automaton a = Automata.makeAnyString();
      inStates += a.getNumStates();
      inTransitions += a.getNumTransitions();
      list.add(a);
    }
    Automaton c = Operations.concatenate(list);
    assertTrue(
        "concatenate of "
            + n
            + " any-string automata ("
            + inStates
            + " states, "
            + inTransitions
            + " transitions) built "
            + c.getNumStates()
            + " states, "
            + c.getNumTransitions()
            + " transitions",
        c.getNumTransitions() <= 10L * (inStates + inTransitions));
  }
}
