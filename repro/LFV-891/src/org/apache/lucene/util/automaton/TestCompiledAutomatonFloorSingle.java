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
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;

public class TestCompiledAutomatonFloorSingle extends LuceneTestCase {

  public void testFloorOfSingleTermAutomaton() {
    CompiledAutomaton compiled = new CompiledAutomaton(Automata.makeString("m"));
    BytesRef floor;
    try {
      floor = compiled.floor(new BytesRef("x"), new BytesRefBuilder());
    } catch (NullPointerException e) {
      throw new AssertionError("floor(\"x\") of {\"m\"}: expected \"m\", got " + e, e);
    }
    assertEquals(new BytesRef("m"), floor);
  }
}
