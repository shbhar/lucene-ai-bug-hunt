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

import org.apache.lucene.tests.util.LuceneTestCase;

public class TestRepeatNegativeMin extends LuceneTestCase {

  // expected: IllegalArgumentException or the language {"", "x"}; actual: accepts "xx", rejects ""
  public void testNegativeMin() {
    Automaton a;
    try {
      a = Operations.repeat(Automata.makeChar('x'), -1, 1);
    } catch (IllegalArgumentException _) {
      return;
    }
    a = Operations.determinize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
    assertFalse("2 repetitions accepted, but max is 1", Operations.run(a, "xx"));
    assertTrue("0 repetitions rejected, but min is -1", Operations.run(a, ""));
  }
}
