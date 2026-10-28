/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.lucene.util.automaton;

import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;

/**
 * CompiledAutomaton#floor: "Finds largest term accepted by this Automaton, that's <= the provided
 * input term ... or null if there is no floor term". floor always walks runAutomaton, which the
 * constructor leaves null for the NONE, ALL and SINGLE types (chosen by default by the public
 * one-argument constructor, which simplifies) and for non-deterministic automata (run by
 * NFARunAutomaton instead).
 */
public class TestCompiledAutomatonFloorTypes extends LuceneTestCase {

  private static String floor(CompiledAutomaton ca, String input) {
    BytesRef r = ca.floor(new BytesRef(input), new BytesRefBuilder());
    return r == null ? null : r.utf8ToString();
  }

  public void testReferenceNormalDfa() {
    // the language {ac, abc} as a DFA: floor works
    Automaton dfa =
        Operations.determinize(
            new RegExp("(a|ab)c").toAutomaton(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
    CompiledAutomaton ca = new CompiledAutomaton(dfa, true, false);
    assertEquals(CompiledAutomaton.AUTOMATON_TYPE.NORMAL, ca.type);
    assertEquals("ac", floor(ca, "b"));
    assertEquals("abc", floor(ca, "abz"));
    assertNull(floor(ca, "a"));
  }

  public void testSingle() {
    CompiledAutomaton ca = new CompiledAutomaton(new RegExp("m").toAutomaton());
    assertEquals(CompiledAutomaton.AUTOMATON_TYPE.SINGLE, ca.type);
    assertEquals("m", floor(ca, "x"));
    assertNull(floor(ca, "a"));
  }

  public void testNone() {
    CompiledAutomaton ca = new CompiledAutomaton(Automata.makeEmpty());
    assertEquals(CompiledAutomaton.AUTOMATON_TYPE.NONE, ca.type);
    assertNull(floor(ca, "x"));
  }

  public void testAll() {
    CompiledAutomaton ca = new CompiledAutomaton(Automata.makeAnyString());
    assertEquals(CompiledAutomaton.AUTOMATON_TYPE.ALL, ca.type);
    assertEquals("x", floor(ca, "x"));
  }

  public void testNonDeterministic() {
    // the same language {ac, abc}, left as the automaton RegExp returns
    Automaton a = new RegExp("(a|ab)c").toAutomaton();
    assumeFalse("needs RegExp to return a non-deterministic automaton", a.isDeterministic());
    CompiledAutomaton ca = new CompiledAutomaton(a);
    assertEquals(CompiledAutomaton.AUTOMATON_TYPE.NORMAL, ca.type);
    assertEquals("ac", floor(ca, "b"));
  }
}
