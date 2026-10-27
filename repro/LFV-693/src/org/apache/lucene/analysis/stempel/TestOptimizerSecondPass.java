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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.pl.PolishAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.egothor.stemmer.Compile;
import org.egothor.stemmer.MultiTrie2;
import org.egothor.stemmer.Optimizer2;
import org.egothor.stemmer.Trie;

/**
 * Optimizer2's class javadoc: joining rows only when they do not collide "allow[s] the stemmer to recognize words for
 * which the original trie was built". A second Optimizer2 (or Optimizer) pass over a trie that already went through a
 * merge pass loses or changes the patch of words the trie was built for. Public API only.
 */
public class TestOptimizerSecondPass extends LuceneTestCase {

  private static final String[] POLISH = {
    "kot kota kotu kotem kocie koty kotów kotom kotami kotach",
    "pies psa psu psem psie psy psów psom psami psach",
    "dom domu domowi domem domy domów domom domami domach",
    "książka książki książce książkę książką książek książkom książkami książkach",
    "czytać czytam czytasz czyta czytamy czytacie czytają czytał czytała czytali",
    "pisać piszę piszesz pisze piszemy piszecie piszą pisał pisała pisali",
    "dobry dobra dobre dobrego dobrej dobremu dobrym dobrą dobrzy dobrych dobrymi",
    "miasto miasta miastu miastem mieście miast miastom miastami miastach",
    "kobieta kobiety kobiecie kobietę kobietą kobiet kobietom kobietami kobietach",
    "okno okna oknu oknem oknie okien oknom oknami oknach",
    "rzeka rzeki rzece rzekę rzeką rzek rzekom rzekami rzekach"
  };

  /** Runs the public Compile tool on the given stemmer table lines and loads the result. */
  private StempelStemmer compile(String method, String... lines) throws Exception {
    Path dir = createTempDir("stempel");
    Path table = dir.resolve("table.txt");
    Files.write(table, List.of(lines), StandardCharsets.UTF_8);
    PrintStream out = System.out;
    System.setOut(new PrintStream(OutputStream.nullOutputStream())); // Compile prints statistics
    try {
      Compile.main(new String[] {method, table.toString()});
    } finally {
      System.setOut(out);
    }
    try (InputStream in = Files.newInputStream(dir.resolve("table.txt.out"))) {
      return new StempelStemmer(StempelStemmer.load(in));
    }
  }

  private static String stem(StempelStemmer s, String word) {
    StringBuilder sb = s.stem(word);
    return sb == null ? null : sb.toString();
  }

  /** Smallest case, public trie API: one word, two Optimizer2 passes. */
  public void testTwoOptimizer2PassesKeepTheOnlyWord() {
    MultiTrie2 trie = new MultiTrie2(false); // backward, as Compile builds it for a "-M..." method
    trie.add("aab", "Da"); // the patch Diff produces for aab -> aa
    Trie once = trie.reduce(new Optimizer2());
    assertEquals("Da", once.getLastOnPath("aab").toString()); // one pass keeps the word
    Trie twice = once.reduce(new Optimizer2());
    assertEquals("second Optimizer2 pass lost the patch of the only word", "Da",
        twice.getLastOnPath("aab").toString());
  }

  /** The same through the Compile tool and StempelStemmer: table line "aa aab" means aab stems to aa. */
  public void testCompileWithTwoOptimizer2Passes() throws Exception {
    assertEquals("aa", stem(compile("-M2", "aa aab"), "aab")); // control: one pass
    assertEquals("aa", stem(compile("-M22", "aa aab"), "aab"));
  }

  /** Polish table, the bundled table's method (-0ME2) versus the same plus a second Optimizer2 pass. */
  public void testPolishTrainingWordsSurviveSecondPass() throws Exception {
    StempelStemmer once = compile("-0ME2", POLISH);
    StempelStemmer twice = compile("-0ME22", POLISH);
    List<String> changed = new ArrayList<>();
    int forms = 0;
    for (String line : POLISH) {
      String[] w = line.split(" ");
      for (int i = 1; i < w.length; i++) {
        forms++;
        String a = stem(once, w[i]);
        String b = stem(twice, w[i]);
        if (a == null ? b != null : a.equals(b) == false) {
          changed.add(w[i] + ": " + a + " -> " + b);
        }
      }
    }
    assertTrue(changed.size() + " of " + forms + " training forms changed their stem, e.g. "
        + changed.subList(0, Math.min(5, changed.size())), changed.isEmpty());
  }

  /** The reduction applied to an already reduced table: the bundled Polish table. */
  public void testOptimizer2OnBundledTableKeepsStems() {
    StempelStemmer original = new StempelStemmer(PolishAnalyzer.getDefaultTable());
    StempelStemmer reduced =
        new StempelStemmer(PolishAnalyzer.getDefaultTable().reduce(new Optimizer2()));
    String[] words = {
      "książkami", "czytałam", "dobrego", "miastach", "kobietami", "oknami", "domach", "studentów"
    };
    List<String> changed = new ArrayList<>();
    for (String w : words) {
      String a = stem(original, w);
      String b = stem(reduced, w);
      if (a == null ? b != null : a.equals(b) == false) {
        changed.add(w + ": " + a + " -> " + b);
      }
    }
    assertTrue(changed.size() + " of " + words.length + " stems changed: " + changed, changed.isEmpty());
  }
}
