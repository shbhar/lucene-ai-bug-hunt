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

import org.apache.lucene.analysis.pl.PolishAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.egothor.stemmer.MultiTrie2;
import org.egothor.stemmer.Reduce;
import org.egothor.stemmer.Trie;

/**
 * Reduce.optimize: "Optimize (remove holes in the rows) the given Trie and return the restructured Trie." It throws
 * IndexOutOfBoundsException for every trie, because it walks the new, still empty row list instead of the trie's rows.
 */
public class TestReduceBase extends LuceneTestCase {

  public void testReduceOnSmallTrie() {
    MultiTrie2 trie = new MultiTrie2(false);
    trie.add("aab", "Da");
    Trie reduced = trie.reduce(new Reduce());
    assertEquals("Da", reduced.getLastOnPath("aab").toString());
  }

  public void testReduceOnBundledTable() {
    Trie reduced = PolishAnalyzer.getDefaultTable().reduce(new Reduce());
    assertEquals(
        String.valueOf(new StempelStemmer(PolishAnalyzer.getDefaultTable()).stem("książkami")),
        String.valueOf(new StempelStemmer(reduced).stem("książkami")));
  }
}
