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
package org.apache.lucene.queryparser;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.RegExp;

/**
 * Each '+' applied to an expression that already ends in '+' doubles the automaton RegExp builds, so the regexp
 * "ab" followed by n '+' (the language ab+, 3 states in 9.x) becomes an automaton of about 2^n states. Nothing bounds
 * it since 10.0: a 22-character regexp typed into a query parser exhausts a 1 GB heap.
 */
public class TestRegexpNestedPlusBlowup extends LuceneTestCase {
  private static final int N = 14; // "ab" + 14 '+' : 16-character regexp

  private static String regexp() {
    return "ab" + "+".repeat(N);
  }

  /** Control: a single '+' and nested '*' stay small. */
  public void testSmallControls() {
    assertTrue(new RegExp("ab+").toAutomaton().getNumStates() <= 4);
    assertTrue(new RegExp("ab" + "*".repeat(N)).toAutomaton().getNumStates() <= 4);
  }

  /** ab++...+ denotes ab+; its automaton must not grow exponentially with the number of '+'. */
  public void testNestedPlusAutomatonSize() {
    Automaton a = new RegExp(regexp()).toAutomaton();
    assertTrue("states=" + a.getNumStates(), a.getNumStates() < 1000); // fails: 16386 states
  }

  /** The same through RegexpQuery and both query parsers, i.e. from untrusted query text. */
  public void testThroughQueryParsers() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    Query classic = new QueryParser("f", analyzer).parse("/" + regexp() + "/");
    Query flexible = new StandardQueryParser(analyzer).parse("/" + regexp() + "/", "f");
    for (Query q : new Query[] {new RegexpQuery(new Term("f", regexp())), classic, flexible}) {
      int states = ((RegexpQuery) q).getAutomaton().getNumStates();
      assertTrue(q + " states=" + states, states < 1000); // fails: 16386 states each
    }
  }
}
