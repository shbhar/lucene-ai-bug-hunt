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
package org.apache.lucene.analysis.stempel;

import org.apache.lucene.tests.util.LuceneTestCase;
import org.egothor.stemmer.MultiTrie2;
import org.egothor.stemmer.Optimizer2;
import org.egothor.stemmer.Trie;

public class TestOptimizer2SecondPass extends LuceneTestCase {

  public void testTwoOptimizer2PassesKeepTheOnlyWord() {
    MultiTrie2 trie = new MultiTrie2(false);
    trie.add("aab", "Da");
    Trie twice = trie.reduce(new Optimizer2()).reduce(new Optimizer2());
    // expected: the word the trie was built for keeps its patch "Da"; actual: "" (lost)
    assertEquals("Da", String.valueOf(twice.getLastOnPath("aab")));
  }
}
