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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.pl.PolishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.egothor.stemmer.Gener;
import org.egothor.stemmer.Lift;
import org.egothor.stemmer.MultiTrie2;
import org.egothor.stemmer.Trie;

/**
 * Trie.reduce "return[s] the newly reduced Trie", and PolishAnalyzer.getDefaultTable() "Returns an unmodifiable
 * instance of the default stemmer table". Lift and Gener instead rewrite the cells of the trie they are given, so the
 * input trie (and the JVM-wide default Polish table) answers differently afterwards. Public API only.
 */
public class TestReduceKeepsInput extends LuceneTestCase {

  private static List<String> terms(Analyzer a, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  /** Gener on the shared default table changes what every PolishAnalyzer in the JVM produces. */
  public void testGenerDoesNotModifyTheDefaultTable() throws IOException {
    String text = "książkami rzekami pracowali";
    List<String> before;
    try (Analyzer a = new PolishAnalyzer()) {
      before = terms(a, text);
    }
    Trie reduced = PolishAnalyzer.getDefaultTable().reduce(new Gener());
    assertNotSame(PolishAnalyzer.getDefaultTable(), reduced);
    List<String> after;
    try (Analyzer a = new PolishAnalyzer()) {
      after = terms(a, text);
    }
    assertEquals("terms of a new PolishAnalyzer after reduce(Gener) of the default table", before, after);
  }

  /** Lift on a small trie built through the public API: the input trie starts to answer for a new key. */
  public void testLiftDoesNotModifyItsInput() {
    MultiTrie2 trie = new MultiTrie2(true);
    trie.add("aa", "Da");
    String before = String.valueOf(trie.getLastOnPath("a"));
    Trie reduced = trie.reduce(new Lift(false));
    assertNotSame(trie, reduced);
    assertEquals("getLastOnPath(\"a\") of the input trie after reduce(Lift)", before,
        String.valueOf(trie.getLastOnPath("a")));
  }
}
