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
package org.apache.lucene.search;

import org.apache.lucene.index.Term;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A regexp that repeats a nullable atom n times describes a language with a small automaton:
 * a(.*)^n b is the language of a.*b for every n, and a(x?)^n b is the language of ax{0,n}b, for
 * which RegExp builds 2n transitions. WildcardQuery collapses a run of '*' (#16382). RegexpQuery
 * should not hold an automaton whose size grows with n^2, which is what Operations.concatenate
 * produces when it links each accept state to every following nullable piece.
 */
public class TestRegexpNullableRunSize extends LuceneTestCase {
  private static final int N = 1000;

  private static long transitions(String regexp) {
    return new RegexpQuery(new Term("f", regexp)).getAutomaton().getNumTransitions();
  }

  /** Same language as a.*b; WildcardQuery a*...*b stays at a handful of transitions. */
  public void testRunOfDotStar() {
    long wildcard =
        new WildcardQuery(new Term("f", "a" + "*".repeat(N) + "b")).getAutomaton().getNumTransitions();
    long regexp = transitions("a" + ".*".repeat(N) + "b");
    assertTrue(
        "RegexpQuery /a(.*)^" + N + "b/ holds " + regexp + " transitions (WildcardQuery a(*)^" + N
            + "b: " + wildcard + ")",
        regexp <= 10L * N);
  }

  /** The query for a 2 KB regexp of language a.*b should not report hundreds of MB. */
  public void testRunOfDotStarRamBytesUsed() {
    RegexpQuery q = new RegexpQuery(new Term("f", "a" + ".*".repeat(N) + "b"));
    long bytes = q.ramBytesUsed();
    assertTrue("RegexpQuery /a(.*)^" + N + "b/ ramBytesUsed=" + bytes, bytes < (1L << 20));
  }

  /** a(x?)^n b and ax{0,n}b are the same language; RegExp builds the second in 2n transitions. */
  public void testRunOfOptionalVsBoundedRepeat() {
    long bounded = transitions("ax{0," + N + "}b");
    long optional = transitions("a" + "x?".repeat(N) + "b");
    assertTrue(
        "RegexpQuery /a(x?)^" + N + "b/ holds " + optional + " transitions, /ax{0," + N + "}b/ "
            + bounded,
        optional <= 10L * Math.max(bounded, N));
  }

  /** Other nullable atoms: a star of a class, an optional group, a star of a negated class. */
  public void testOtherNullableAtoms() {
    int n = 500;
    StringBuilder msg = new StringBuilder();
    for (String atom : new String[] {"[a-z]*", "(bc)?", "[^x]*", "(ab)*"}) {
      long t = transitions("a" + atom.repeat(n) + "b");
      if (t > 10L * n) {
        msg.append(" /a(").append(atom).append(")^").append(n).append("b/ -> ").append(t);
      }
    }
    assertTrue("transitions above 10n:" + msg, msg.length() == 0);
  }
}
