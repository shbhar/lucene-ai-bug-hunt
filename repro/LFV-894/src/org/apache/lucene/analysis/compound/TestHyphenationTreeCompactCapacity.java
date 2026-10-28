/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.analysis.compound;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import org.apache.lucene.analysis.compound.hyphenation.HyphenationTree;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.xml.sax.InputSource;

/**
 * A grammar of 1,310 short patterns (far below the "room for about 65000 nodes" that the
 * TernaryTree javadoc promises) cannot be loaded: HyphenationTree.loadPatterns calls
 * trimToSize(), whose compact() step fills a helper TernaryTree through insert(char[] key, int
 * start, char val). That method sizes its node arrays from strlen(key) (counted from index 0, which
 * is the empty string at the start of the compacted key array) instead of strlen(key, start), so the
 * arrays are not grown before a multi-node insert crosses a 2048-node block boundary.
 */
public class TestHyphenationTreeCompactCapacity extends LuceneTestCase {

  /** 1,310 distinct random words of 4-8 letters a-z; fixed seed, so the grammar is always the same. */
  private static List<String> words() {
    Random rnd = new Random(1);
    LinkedHashSet<String> s = new LinkedHashSet<>();
    while (s.size() < 1310) {
      int len = 4 + rnd.nextInt(5);
      StringBuilder sb = new StringBuilder();
      for (int j = 0; j < len; j++) {
        sb.append((char) ('a' + rnd.nextInt(26)));
      }
      s.add(sb.toString());
    }
    return new ArrayList<>(s);
  }

  public void testGrammarOf1310PatternsLoads() throws Exception {
    List<String> words = words();
    StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
    xml.append("<hyphenation-info>\n<patterns>\n");
    for (String w : words) {
      // pattern "a1bcd": a hyphenation value of 1 between the first two letters
      xml.append(w.charAt(0)).append('1').append(w, 1, w.length()).append('\n');
    }
    xml.append("</patterns>\n</hyphenation-info>\n");

    HyphenationTree tree =
        HyphenationCompoundWordTokenFilter.getHyphenationTree(
            new InputSource(new StringReader(xml.toString())));

    assertEquals(words.size(), tree.size());
    for (String w : words) {
      StringBuilder expected = new StringBuilder("01");
      for (int i = 2; i <= w.length(); i++) {
        expected.append('0');
      }
      assertEquals(w, expected.toString(), tree.findPattern(w));
    }
  }
}
