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

public class TestTopoSortSelfLoop extends LuceneTestCase {

  public void testSelfLoopIsACycle() {
    Automaton a = new Automaton();
    int s0 = a.createState();
    int s1 = a.createState();
    a.setAccept(s1, true);
    a.addTransition(s0, s1, 'a');
    a.addTransition(s1, s1, 'b');
    a.finishState();
    // Expected: IllegalArgumentException for the cycle 1 -b-> 1; actual: returns the order [0, 1].
    expectThrows(IllegalArgumentException.class, () -> Operations.topoSortStates(a));
  }
}
