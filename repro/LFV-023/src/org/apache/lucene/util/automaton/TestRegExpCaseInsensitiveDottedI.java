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

public class TestRegExpCaseInsensitiveDottedI extends LuceneTestCase {

  public void testDottedCapitalIMatchesSymmetrically() {
    // expected: /U+0130/ on "i" and /i/ on "U+0130" give the same answer; actual: true, then false
    assertEquals(
        "CASE_INSENSITIVE /U+0130/ on \"i\" vs /i/ on \"U+0130\"",
        matches("\u0130", "i"),
        matches("i", "\u0130"));
  }

  private static boolean matches(String regex, String text) {
    Automaton automaton = new RegExp(regex, RegExp.NONE, RegExp.CASE_INSENSITIVE).toAutomaton();
    return new CharacterRunAutomaton(automaton).run(text);
  }
}
