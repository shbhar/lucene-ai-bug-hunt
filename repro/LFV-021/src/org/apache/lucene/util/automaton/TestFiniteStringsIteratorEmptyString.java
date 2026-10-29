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
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IntsRef;

public class TestFiniteStringsIteratorEmptyString extends LuceneTestCase {

  public void testEmptyStringReturnedOnce() {
    Automaton a = new Automaton();
    int s0 = a.createState();
    int s1 = a.createState();
    a.setAccept(s0, true);
    a.setAccept(s1, true);
    a.addTransition(s0, s1, 'a');
    a.finishState();
    assertTrue(a.isDeterministic());

    List<String> strings = new ArrayList<>();
    FiniteStringsIterator it = new FiniteStringsIterator(a);
    for (IntsRef s = it.next(); s != null; s = it.next()) {
      strings.add(new String(s.ints, s.offset, s.length));
    }
    // accepts {"", "a"}: expected 2 strings, actual 3 ("" is returned again at the end)
    assertEquals("strings: " + strings, 2, strings.size());
  }
}
