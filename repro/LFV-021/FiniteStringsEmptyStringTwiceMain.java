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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.FiniteStringsIterator;
import org.apache.lucene.util.automaton.LimitedFiniteStringsIterator;

/**
 * Cross-version check (main() form, so it runs on releases whose test framework predates
 * org.apache.lucene.tests.util.LuceneTestCase). Uses only API present since 5.3.0, when
 * FiniteStringsIterator was added.
 *
 * <p>Automaton 0 (accepting) --a--> 1 (accepting), language {"", "a"}. The iterator must return 2
 * strings. Exits non-zero (AssertionError) when it returns a duplicate.
 */
public class FiniteStringsEmptyStringTwiceMain {
  public static void main(String[] args) {
    Automaton a = new Automaton();
    int s0 = a.createState();
    int s1 = a.createState();
    a.setAccept(s0, true);
    a.setAccept(s1, true);
    a.addTransition(s0, s1, 'a');
    a.finishState();

    List<String> got = new ArrayList<>();
    FiniteStringsIterator it = new FiniteStringsIterator(a);
    for (IntsRef r; (r = it.next()) != null; ) {
      got.add('"' + new String(r.ints, r.offset, r.length) + '"');
    }
    LimitedFiniteStringsIterator lit = new LimitedFiniteStringsIterator(a, -1);
    while (lit.next() != null) {}

    System.out.println(
        "isDeterministic="
            + a.isDeterministic()
            + " FiniteStringsIterator -> "
            + got
            + " LimitedFiniteStringsIterator.size()="
            + lit.size());
    if (got.size() != 2 || lit.size() != 2) {
      throw new AssertionError(
          "expected 2 strings for {\"\", \"a\"}, got " + got + ", size()=" + lit.size());
    }
  }
}
