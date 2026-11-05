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

import java.util.List;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestBinaryRangeUnionDeterminize extends LuceneTestCase {

  // Expected: a DFA accepting {0xFF,'c'}; actual: determinize throws in StateSet.decr (empty arc [256,255]).
  public void testUnionOfOpenEndedRangesWith0xFFLowerTerm() {
    Automaton a = TermRangeQuery.toAutomaton(new BytesRef(new byte[] {(byte) 0xFF, 'a'}), null, true, true);
    Automaton b = TermRangeQuery.toAutomaton(new BytesRef(new byte[] {(byte) 0xFF, 'b'}), null, true, true);
    Automaton d =
        Operations.determinize(
            Operations.union(List.of(a, b)), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
    byte[] term = {(byte) 0xFF, 'c'};
    assertTrue(new ByteRunAutomaton(d, true).run(term, 0, term.length));
  }
}
