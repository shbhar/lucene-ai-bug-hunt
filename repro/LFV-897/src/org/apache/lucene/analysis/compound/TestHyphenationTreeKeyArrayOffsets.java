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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import org.apache.lucene.analysis.compound.hyphenation.HyphenationTree;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.xml.sax.InputSource;

/**
 * TernaryTree stores offsets into its key array (kv) in char slots (lo[p] = (char) kv.alloc(..)),
 * so once the key array grows past 65,535 chars the offsets wrap and later key tails overwrite
 * earlier ones. The tree here has only about 6,000 nodes (the javadoc promises "room for about
 * 65000 nodes"), loads without any error, and then silently returns wrong or missing values.
 */
public class TestHyphenationTreeKeyArrayOffsets extends LuceneTestCase {

  /** 2,600 distinct random words of 30 letters a-z; fixed seed, so the grammar is always the same. */
  private static List<String> words() {
    Random rnd = new Random(7);
    LinkedHashSet<String> s = new LinkedHashSet<>();
    while (s.size() < 2600) {
      StringBuilder sb = new StringBuilder();
      for (int j = 0; j < 30; j++) {
        sb.append((char) ('a' + rnd.nextInt(26)));
      }
      s.add(sb.toString());
    }
    return new ArrayList<>(s);
  }

  public void testLongPatternsKeepTheirValues() throws Exception {
    List<String> words = words();
    StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
    xml.append("<hyphenation-info>\n<patterns>\n");
    for (String w : words) {
      xml.append(w.charAt(0)).append('1').append(w, 1, w.length()).append('\n');
    }
    xml.append("</patterns>\n</hyphenation-info>\n");

    HyphenationTree tree =
        HyphenationCompoundWordTokenFilter.getHyphenationTree(
            new InputSource(new StringReader(xml.toString())));

    ByteArrayOutputStream stats = new ByteArrayOutputStream();
    tree.printStats(new PrintStream(stats, true, StandardCharsets.UTF_8));
    String nodeLine = stats.toString(StandardCharsets.UTF_8).lines()
        .filter(l -> l.startsWith("Node count")).findFirst().orElse("");
    int nodes = Integer.parseInt(nodeLine.replaceAll("[^0-9]", ""));
    assertTrue("the tree stays far below 65000 nodes: " + nodes, nodes < 20000);

    String expected = "01" + "0".repeat(29);
    int wrong = 0;
    String firstWrong = null;
    for (String w : words) {
      String got = tree.findPattern(w);
      if (!expected.equals(got)) {
        if (firstWrong == null) {
          firstWrong = "findPattern(" + w + ") = \"" + got + "\"";
        }
        wrong++;
      }
    }
    assertEquals(
        "patterns whose values were lost (" + firstWrong + "); tree.size()=" + tree.size(),
        0,
        wrong);
    assertEquals(words.size(), tree.size());
  }
}
