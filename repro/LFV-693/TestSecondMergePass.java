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
package org.egothor.stemmer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.stempel.StempelStemmer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A merge pass (Optimizer or Optimizer2) over a trie that a merge pass has already reduced must
 * keep the words the trie was built for, as a single pass does.
 */
public class TestSecondMergePass extends LuceneTestCase {

  /** The four lines of TestCompile's testRules.txt: the first word of a line is the stem. */
  private static final List<String> RULES =
      List.of(
          "act acted acting actor",
          "walk walked walking",
          "wander wandered wanderer",
          "want wanted wanting");

  private static String str(CharSequence cs) {
    return cs == null ? null : cs.toString();
  }

  /** One word in a backward trie: aab stems to aa, the patch is "Da". */
  public void testSecondOptimizer2PassKeepsTheOnlyWord() {
    Trie trie = new Trie(false);
    trie.add("aab", "Da");
    Trie once = trie.reduce(new Optimizer2());
    assertEquals("one Optimizer2 pass", "Da", str(once.getLastOnPath("aab")));
    Trie twice = once.reduce(new Optimizer2());
    assertEquals("two Optimizer2 passes", "Da", str(twice.getLastOnPath("aab")));
  }

  /** Optimizer shares Optimizer2's optimize(), so it has the same problem. */
  public void testSecondOptimizerPassKeepsBothWords() {
    Trie trie = new Trie(false);
    trie.add("aa", "Da");
    trie.add("ab", "Db");
    Trie once = trie.reduce(new Optimizer());
    assertEquals("one Optimizer pass", "Da", str(once.getLastOnPath("aa")));
    assertEquals("one Optimizer pass", "Db", str(once.getLastOnPath("ab")));
    Trie twice = once.reduce(new Optimizer());
    assertEquals("two Optimizer passes", "Da", str(twice.getLastOnPath("aa")));
    assertEquals("two Optimizer passes", "Db", str(twice.getLastOnPath("ab")));
  }

  /** The Compile tool: one Optimizer2 pass ("2") versus two ("22"). */
  public void testCompileWithTwoOptimizer2Passes() throws Exception {
    Map<String, List<String>> wrong = new LinkedHashMap<>();
    for (String method : new String[] {"-2", "-M2", "-22", "-M22"}) {
      wrong.put(method, wronglyStemmed(method));
    }
    List<String> none = List.of();
    assertEquals(Map.of("-2", none, "-M2", none, "-22", none, "-M22", none), wrong);
  }

  /** Compiles RULES with the given method and lists the training words that lose their stem. */
  private List<String> wronglyStemmed(String method) throws Exception {
    Path dir = createTempDir("secondMergePass");
    Path rules = dir.resolve("rules.txt");
    Files.write(rules, RULES, StandardCharsets.UTF_8);
    Compile.main(new String[] {method, rules.toAbsolutePath().toString()});
    StempelStemmer stemmer;
    try (InputStream in = Files.newInputStream(dir.resolve("rules.txt.out"))) {
      stemmer = new StempelStemmer(StempelStemmer.load(in));
    }
    List<String> wrong = new ArrayList<>();
    for (String line : RULES) {
      String[] words = line.split(" ");
      for (int i = 1; i < words.length; i++) {
        String got = str(stemmer.stem(words[i]));
        if (words[0].equals(got) == false) {
          wrong.add(words[i] + " -> " + got);
        }
      }
    }
    return wrong;
  }
}
