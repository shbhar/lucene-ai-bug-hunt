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

import java.util.Locale;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.automaton.Automaton;

public class TestAutomatonToTokenStreamUnreachableState extends BaseTokenStreamTestCase {

  public void testUnreachableStateIsNotACycle() throws Exception {
    // 0 -a-> 2 <-b- 1, state 2 accepts: a finite DAG, and nothing leads to state 1
    Automaton.Builder builder = new Automaton.Builder();
    builder.createState(); // 0, the start state
    builder.createState(); // 1
    builder.createState(); // 2
    builder.addTransition(0, 2, 'a');
    builder.addTransition(1, 2, 'b');
    builder.setAccept(2, true);
    Automaton automaton = builder.finish();

    TokenStream ts;
    try {
      ts = AutomatonToTokenStream.toTokenStream(automaton);
    } catch (IllegalArgumentException e) {
      // rejecting the unreachable state is acceptable; reporting a cycle is not
      if (e.getMessage().toLowerCase(Locale.ROOT).contains("cycle")) {
        throw new AssertionError("acyclic automaton reported as cyclic: " + e.getMessage(), e);
      }
      return;
    }
    assertTokenStreamContents(ts, new String[] {"a"});
  }
}
