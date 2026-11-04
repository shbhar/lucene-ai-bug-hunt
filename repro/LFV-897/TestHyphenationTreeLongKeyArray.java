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
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import org.apache.lucene.analysis.compound.hyphenation.HyphenationTree;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.xml.sax.InputSource;

public class TestHyphenationTreeLongKeyArray extends LuceneTestCase {

  public void testEveryPatternKeepsItsValues() throws Exception {
    // 2,600 distinct 30-letter patterns such as "q1mdkiasnm...": the tree needs only 4,771 nodes
    // (the TernaryTree javadoc gives "room for about 65000 nodes"), but its key array passes
    // 65,535 chars while the patterns are loaded.
    Random random = new Random(7);
    Set<String> words = new LinkedHashSet<>();
    while (words.size() < 2600) {
      char[] word = new char[30];
      for (int i = 0; i < word.length; i++) {
        word[i] = (char) ('a' + random.nextInt(26));
      }
      words.add(new String(word));
    }
    StringBuilder xml = new StringBuilder("<hyphenation-info><patterns>\n");
    for (String w : words) {
      xml.append(w.charAt(0)).append('1').append(w, 1, w.length()).append('\n');
    }
    xml.append("</patterns></hyphenation-info>\n");

    HyphenationTree tree =
        HyphenationCompoundWordTokenFilter.getHyphenationTree(
            new InputSource(new StringReader(xml.toString())));

    String expected = "01" + "0".repeat(29); // a hyphen after the first letter, nowhere else
    int lost = 0;
    for (String w : words) {
      if (!expected.equals(tree.findPattern(w))) {
        lost++;
      }
    }
    assertEquals("patterns that lost their values", 0, lost);
  }
}
