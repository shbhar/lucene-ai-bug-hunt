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

/**
 * AutomatonToTokenStream reads indegree[0] without checking that the automaton has a state, so the
 * empty-language automaton (0 states), which Automata.makeEmpty() and Operations return for an empty
 * language, fails with ArrayIndexOutOfBoundsException.
 */
public class TestAutomatonToTokenStreamEmptyAutomaton extends BaseTokenStreamTestCase {

  public void testMakeEmpty() throws IOException {
    Automaton empty = Automata.makeEmpty();
    assertEquals(0, empty.getNumStates());
    // a finite DAG with an empty language: expect a stream without tokens (as for
    // Automata.makeEmptyString()); actual: ArrayIndexOutOfBoundsException
    TokenStream ts = AutomatonToTokenStream.toTokenStream(empty);
    assertTokenStreamContents(ts, new String[0]);
  }

  public void testEmptyIntersection() throws IOException {
    Automaton a = Operations.intersection(Automata.makeString("a"), Automata.makeString("b"));
    assertTrue(Operations.isEmpty(a));
    TokenStream ts = AutomatonToTokenStream.toTokenStream(a);
    assertTokenStreamContents(ts, new String[0]);
  }

  public void testEmptyStringAutomatonForComparison() throws IOException {
    // control: the 1-state automaton of the empty string already gives a stream without tokens
    TokenStream ts = AutomatonToTokenStream.toTokenStream(Automata.makeEmptyString());
    assertTokenStreamContents(ts, new String[0]);
  }
}
