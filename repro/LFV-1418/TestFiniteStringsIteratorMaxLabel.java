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

public class TestFiniteStringsIteratorMaxLabel extends LuceneTestCase {

  public void testLabelMaxValue() {
    // expected: the only accepted string [Integer.MAX_VALUE], then null; actual: next() returns [Integer.MIN_VALUE]
    Automaton a = new Automaton();
    a.createState();
    a.setAccept(a.createState(), true);
    a.addTransition(0, 1, Integer.MAX_VALUE);
    a.finishState();
    FiniteStringsIterator it = new FiniteStringsIterator(a);
    assertEquals(Integer.MAX_VALUE, it.next().ints[0]);
    assertNull(it.next());
  }
}
