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
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.Transition;

/**
 * AutomatonToTokenStream counts in-degrees over all states but starts its topological sort only
 * from state 0, so an acyclic automaton with a state that is not reachable from state 0 (and has a
 * transition) is rejected with "Cycle found in automaton" or "Start node has incoming edges".
 */
public class TestAutomatonToTokenStreamUnreachableState extends BaseTokenStreamTestCase {

  public void testUnreachableStateIsNotACycle() throws IOException {
    Automaton.Builder b = new Automaton.Builder();
    int start = b.createState();
    int unreachable = b.createState();
    int accept = b.createState();
    b.addTransition(start, accept, 'a');
    b.addTransition(unreachable, accept, 'b'); // nothing leads to 'unreachable'
    b.setAccept(accept, true);
    Automaton a = b.finish();
    assertTrue("finite DAG, as the javadoc requires", isAcyclic(a));
    // actual: IllegalArgumentException("Cycle found in automaton")
    TokenStream ts = AutomatonToTokenStream.toTokenStream(a);
    assertTokenStreamContents(
        ts, new String[] {"a"}, new int[] {0}, new int[] {1}, new int[] {1}, new int[] {1}, 1);
  }

  public void testUnreachableStateIntoStartIsNotACycle() throws IOException {
    Automaton.Builder b = new Automaton.Builder();
    int start = b.createState();
    int accept = b.createState();
    int unreachable = b.createState();
    b.addTransition(start, accept, 'a');
    b.addTransition(unreachable, start, 'b'); // nothing leads to 'unreachable'
    b.setAccept(accept, true);
    Automaton a = b.finish();
    assertTrue("finite DAG, as the javadoc requires", isAcyclic(a));
    // actual: IllegalArgumentException("Start node has incoming edges, creating cycle")
    TokenStream ts = AutomatonToTokenStream.toTokenStream(a);
    assertTokenStreamContents(
        ts, new String[] {"a"}, new int[] {0}, new int[] {1}, new int[] {1}, new int[] {1}, 1);
  }

  /**
   * TokenStreamToAutomaton leaves such a state for a stream whose only token was removed (a stop
   * word): the trailing-hole states are not connected to state 0.
   */
  public void testTokenStreamToAutomatonOutputOfStopWordOnlyText() throws IOException {
    Analyzer analyzer =
        new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true, MockTokenFilter.ENGLISH_STOPSET);
    Automaton a;
    try (TokenStream in = analyzer.tokenStream("field", "the")) {
      a = new TokenStreamToAutomaton().toAutomaton(in);
    }
    analyzer.close();
    assertTrue("finite DAG, as the javadoc requires", isAcyclic(a));
    // the language is empty: expect a stream without tokens; actual: "Cycle found in automaton"
    TokenStream ts = AutomatonToTokenStream.toTokenStream(a);
    assertTokenStreamContents(ts, new String[0]);
  }

  /** true if no state lies on a cycle (checked over all states, reachable or not). */
  private static boolean isAcyclic(Automaton a) {
    int n = a.getNumStates();
    int[] color = new int[n]; // 0 new, 1 on stack, 2 done
    for (int s = 0; s < n; s++) {
      if (color[s] == 0 && onCycle(a, s, color)) {
        return false;
      }
    }
    return true;
  }

  private static boolean onCycle(Automaton a, int s, int[] color) {
    color[s] = 1;
    Transition t = new Transition();
    int count = a.initTransition(s, t);
    for (int i = 0; i < count; i++) {
      a.getNextTransition(t);
      if (color[t.dest] == 1 || (color[t.dest] == 0 && onCycle(a, t.dest, color))) {
        return true;
      }
    }
    color[s] = 2;
    return false;
  }
}
