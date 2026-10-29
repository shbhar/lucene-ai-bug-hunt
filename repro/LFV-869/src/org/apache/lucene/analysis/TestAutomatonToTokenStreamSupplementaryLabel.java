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

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.automaton.Automata;

public class TestAutomatonToTokenStreamSupplementaryLabel extends LuceneTestCase {

  public void testSupplementaryLabel() throws Exception {
    String longI = new String(Character.toChars(0x10400));
    try (TokenStream ts = AutomatonToTokenStream.toTokenStream(Automata.makeString(longI))) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      assertTrue(ts.incrementToken());
      // expected: token text U+10400 (the edge label); actual: U+0400 (the label cast to char)
      assertEquals(longI, term.toString());
    }
  }
}
