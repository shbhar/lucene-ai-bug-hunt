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

public class TestRegExpIntervalPlusSign extends LuceneTestCase {

  public void testPlusSignOnIntervalBounds() {
    // <+1-+9> must be rejected or, like <1-9>, match "5"; instead the '+' counts as a digit (<01-09>)
    RegExp re;
    try {
      re = new RegExp("<+1-+9>");
    } catch (IllegalArgumentException rejected) {
      return;
    }
    CharacterRunAutomaton run =
        new CharacterRunAutomaton(
            Operations.determinize(re.toAutomaton(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT));
    assertTrue(
        "expected <+1-+9> to match \"5\" like <1-9>, but it does not (matches \"05\": "
            + run.run("05")
            + ")",
        run.run("5"));
  }
}
