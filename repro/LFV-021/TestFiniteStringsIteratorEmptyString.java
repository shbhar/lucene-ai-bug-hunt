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
import java.util.Collections;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRef;

/**
 * FiniteStringsIterator must return each accepted string of a deterministic automaton exactly
 * once. The class javadoc allows duplicates only "if the automaton is not determinized".
 *
 * <p>When the start state is accepting and also has an outgoing transition, the empty string is
 * returned twice: once up front, and once more when the start state is popped at the end.
 *
 * <p>Uses only public API.
 */
public class TestFiniteStringsIteratorEmptyString extends LuceneTestCase {

  /** Drains the iterator into a list of strings (one per returned IntsRef, in order). */
  private static List<String> drain(FiniteStringsIterator it) {
    List<String> out = new ArrayList<>();
    for (IntsRef r; (r = it.next()) != null; ) {
      out.add(new String(r.ints, r.offset, r.length));
    }
    return out;
  }

  /** Iteration order is implementation dependent, so compare sorted copies. */
  private static void assertStrings(List<String> expected, List<String> actual) {
    List<String> e = new ArrayList<>(expected);
    List<String> a = new ArrayList<>(actual);
    Collections.sort(e);
    Collections.sort(a);
    assertEquals("strings returned by the iterator, in order: " + quote(actual), e, a);
  }

  private static String quote(List<String> l) {
    List<String> q = new ArrayList<>();
    for (String s : l) {
      q.add('"' + s + '"');
    }
    return q.toString();
  }

  /** 0 (accepting) --a--> 1 (accepting): the language is {"", "a"}. */
  private static Automaton emptyOrA() {
    Automaton a = new Automaton();
    int s0 = a.createState();
    int s1 = a.createState();
    a.setAccept(s0, true);
    a.setAccept(s1, true);
    a.addTransition(s0, s1, 'a');
    a.finishState();
    return a;
  }

  /** Hand-built two-state DFA for {"", "a"}: expected ["", "a"]. */
  public void testDeterministicEmptyOrA() {
    Automaton a = emptyOrA();
    assertTrue(a.isDeterministic());
    assertStrings(List.of("", "a"), drain(new FiniteStringsIterator(a)));
  }

  /** The same language built by Automata.makeStringUnion ("deterministic and minimal"). */
  public void testStringUnionEmptyOrA() {
    Automaton a = Automata.makeStringUnion(List.of(new BytesRef(""), new BytesRef("a")));
    assertTrue(a.isDeterministic());
    assertStrings(List.of("", "a"), drain(new FiniteStringsIterator(a)));
  }

  /** LimitedFiniteStringsIterator.size() is "Number of iterated finite strings": expected 2. */
  public void testLimitedSize() {
    Automaton a = Automata.makeStringUnion(List.of(new BytesRef(""), new BytesRef("a")));
    LimitedFiniteStringsIterator it = new LimitedFiniteStringsIterator(a, -1);
    List<String> got = drain(it);
    assertEquals("size() after iterating " + quote(got), 2, it.size());
  }

  /** Control: accepting start state with no outgoing transition, {""}. Passes. */
  public void testControlEmptyStringOnly() {
    assertStrings(List.of(""), drain(new FiniteStringsIterator(Automata.makeEmptyString())));
  }

  /** Control: start state not accepting, {"a", "b"}. Passes. */
  public void testControlNoEmptyString() {
    Automaton a = Automata.makeStringUnion(List.of(new BytesRef("a"), new BytesRef("b")));
    assertStrings(List.of("a", "b"), drain(new FiniteStringsIterator(a)));
  }
}
