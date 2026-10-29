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
package org.apache.lucene.analysis;

import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.automaton.Automata;

public class TestAutomatonToTokenStreamZeroStates extends BaseTokenStreamTestCase {

  public void testMakeEmpty() throws Exception {
    // Automata.makeEmpty() is a finite DAG with 0 states (the empty language)
    TokenStream ts;
    try {
      ts = AutomatonToTokenStream.toTokenStream(Automata.makeEmpty());
    } catch (IllegalArgumentException acceptable) {
      return;
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new AssertionError(
          "expected an empty TokenStream or IllegalArgumentException, got " + e, e);
    }
    assertTokenStreamContents(ts, new String[0]);
  }
}
