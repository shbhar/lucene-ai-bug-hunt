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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.AutomatonQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Operations.isTotal(a) must return true only if the automaton accepts every string. For a
 * deterministic automaton that has a reachable dead (sink) state it returns true even though some
 * strings are rejected, and an AutomatonQuery built from such an automaton then matches every term.
 *
 * <p>Uses public API only.
 */
public class TestIsTotalDeadState extends LuceneTestCase {

  /**
   * A complete DFA with an explicit sink, over all code points. It accepts exactly the strings in
   * which every code point is &gt;= 'a'. State 0: accepting, ['a'..MAX] loops to 0, [0..'a'-1] goes
   * to the sink. Sink: not accepting, [0..MAX] loops to the sink (it can never reach an accept).
   */
  private static Automaton completeDfaWithSink() {
    Automaton a = new Automaton();
    int start = a.createState();
    int sink = a.createState();
    a.setAccept(start, true);
    a.addTransition(start, sink, Character.MIN_CODE_POINT, 'a' - 1);
    a.addTransition(start, start, 'a', Character.MAX_CODE_POINT);
    a.addTransition(sink, sink, Character.MIN_CODE_POINT, Character.MAX_CODE_POINT);
    a.finishState();
    return a;
  }

  /** isTotal(a): documented as "Returns true if the given automaton accepts all strings." */
  public void testIsTotalFalseForDfaWithReachableSink() {
    Automaton a = completeDfaWithSink();
    assertTrue("precondition: deterministic", a.isDeterministic());
    assertTrue("precondition: has a dead state", Operations.hasDeadStates(a));
    assertTrue(Operations.run(a, "b"));
    assertFalse("\"0\" is rejected", Operations.run(a, "0"));
    assertFalse(
        "isTotal(a) must be false for a DFA that rejects \"0\"", Operations.isTotal(a));
  }

  /** isTotal(a, 0, 255) is the call CompiledAutomaton makes for binary automata. */
  public void testIsTotalBinaryFalseForByteDfaWithDeadState() {
    Automaton a = new Automaton();
    int start = a.createState();
    int dead = a.createState();
    a.setAccept(start, true);
    a.addTransition(start, start, 0, 'x' - 1);
    a.addTransition(start, dead, 'x', 'x');
    a.addTransition(start, start, 'x' + 1, 0xff);
    a.finishState();
    assertTrue("precondition: deterministic", a.isDeterministic());
    assertFalse("byte 'x' is rejected", Operations.run(a, "x"));
    assertFalse(
        "isTotal(a, 0, 255) must be false for a byte DFA that rejects 'x'",
        Operations.isTotal(a, 0, 0xff));
  }

  /** AutomatonQuery javadoc: "terms that are accepted are considered a match". */
  public void testAutomatonQueryMatchesOnlyAcceptedTerms() throws Exception {
    assertEquals(
        "only \"b\" and \"zz\" are accepted", 2, countMatches(completeDfaWithSink()));
  }

  /** Control: the same language without the dead state gives the right answers. */
  public void testControlSameLanguageWithoutDeadState() throws Exception {
    Automaton clean = Operations.removeDeadStates(completeDfaWithSink());
    assertFalse(Operations.hasDeadStates(clean));
    assertFalse(Operations.run(clean, "0"));
    assertFalse(Operations.isTotal(clean));
    assertEquals(2, countMatches(clean));
  }

  private int countMatches(Automaton a) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        for (String v : new String[] {"0", "b", "zz"}) {
          Document doc = new Document();
          doc.add(new StringField("f", v, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        return new IndexSearcher(r).count(new AutomatonQuery(new Term("f", ""), a));
      }
    }
  }
}
