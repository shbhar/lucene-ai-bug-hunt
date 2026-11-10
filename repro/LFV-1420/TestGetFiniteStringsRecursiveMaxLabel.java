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
package org.apache.lucene.tests.util.automaton;

import java.util.Set;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.automaton.Automaton;

public class TestGetFiniteStringsRecursiveMaxLabel extends LuceneTestCase {

  public void testLabelMaxValue() {
    // expected: only the accepted string [Integer.MAX_VALUE]; actual: also [Integer.MIN_VALUE]
    Automaton a = new Automaton();
    a.createState();
    a.setAccept(a.createState(), true);
    a.addTransition(0, 1, Integer.MAX_VALUE);
    a.finishState();
    assertEquals(
        Set.of(new IntsRef(new int[] {Integer.MAX_VALUE}, 0, 1)),
        AutomatonTestUtil.getFiniteStringsRecursive(a, 1));
  }
}
