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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import org.apache.lucene.analysis.pl.PolishAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.egothor.stemmer.Compile;
import org.egothor.stemmer.Gener;
import org.egothor.stemmer.Row;
import org.egothor.stemmer.Trie;

/**
 * Independent check: a stemmer table reduced with Gener ('G' in the Compile method string) must
 * load back and answer like the reduced trie that was stored. Uses only public API.
 */
public class TestGenerTableRoundTrip extends LuceneTestCase {

  /**
   * Row level: Row.store followed by Row(DataInput) must give back the same row, also when the row
   * holds a cell that has neither a command nor a reference (a state Row.setCmd accepts).
   */
  public void testRowWithEmptyCellRoundTrip() throws Exception {
    Row first = new Row();
    first.setCmd('a', 0);
    first.setCmd('b', -1); // cell with cmd = -1 and ref = -1
    Row second = new Row();
    second.setCmd('x', 3);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      first.store(out);
      second.store(out);
    }

    DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    Row first2 = new Row(in);
    Row second2 = new Row(in);
    assertEquals(0, first2.getCmd('a'));
    assertEquals(first.getCells(), first2.getCells());
    assertEquals(3, second2.getCmd('x'));
    assertEquals(second.getCells(), second2.getCells());
  }

  /**
   * End to end with the Compile tool: a table compiled with method "G" must load with
   * StempelStemmer.load and answer like the same dictionary compiled without G and reduced with
   * Gener in memory.
   */
  public void testCompileWithGenerLoads() throws Exception {
    StringBuilder dict = new StringBuilder();
    // 20 inflected forms under one prefix: one row with 20 commands, each counted once, so Gener
    // removes all of them (frame = 20 / 10 = 2 > 1) and the row is dropped.
    dict.append("b");
    for (char c = 'a'; c < 'a' + 20; c++) {
      dict.append(' ').append('b').append(c);
    }
    dict.append('\n');
    dict.append("c ca cb\n");
    dict.append("dog dogs doggy\n");
    dict.append("walk walked walking walks\n");

    Path dir = createTempDir("v1gener");
    Path plain = dir.resolve("plain.txt");
    Path gener = dir.resolve("gener.txt");
    Files.writeString(plain, dict, StandardCharsets.UTF_8);
    Files.writeString(gener, dict, StandardCharsets.UTF_8);

    Compile.main(new String[] {"X", plain.toString()}); // no reduction letter
    Compile.main(new String[] {"G", gener.toString()}); // Gener

    Trie expected = load(dir.resolve("plain.txt.out")).reduce(new Gener());
    Trie actual = load(dir.resolve("gener.txt.out")); // fails here on main

    List<String> probes = new ArrayList<>();
    for (String line : dict.toString().split("\n")) {
      for (String w : line.split(" ")) {
        probes.add(w);
      }
    }
    probes.add("dogsy");
    probes.add("walkings");
    assertSameAnswers(expected, actual, probes);
  }

  /**
   * The bundled Polish table (method "-0ME2") further reduced with Gener, stored in the same
   * format Compile writes, must load back and answer like the in-memory reduced table.
   */
  public void testBundledTableReducedWithGenerRoundTrip() throws Exception {
    Trie table;
    try (InputStream is =
        PolishAnalyzer.class.getResourceAsStream(PolishAnalyzer.DEFAULT_STEMMER_FILE)) {
      table = StempelStemmer.load(is); // private copy, not the shared default table
    }
    Trie reduced = table.reduce(new Gener());

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeUTF("-0ME2G"); // what Compile writes for method -0ME2G
      reduced.store(out);
    }
    Trie loaded = StempelStemmer.load(new ByteArrayInputStream(bytes.toByteArray()));

    Random r = new Random(42);
    String alphabet = "aąbcćdeęfghijklłmnńoóprsśtuwyzźż";
    List<String> probes = new ArrayList<>();
    for (int i = 0; i < 20000; i++) {
      int len = 3 + r.nextInt(8);
      StringBuilder sb = new StringBuilder();
      for (int j = 0; j < len; j++) {
        sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
      }
      probes.add(sb.toString());
    }
    assertSameAnswers(reduced, loaded, probes);
  }

  private static Trie load(Path p) throws Exception {
    try (InputStream is = Files.newInputStream(p)) {
      return StempelStemmer.load(is);
    }
  }

  private static void assertSameAnswers(Trie expected, Trie actual, List<String> probes) {
    int diffs = 0;
    String first = null;
    for (String w : probes) {
      String k = w.toLowerCase(Locale.ROOT);
      String e = Objects.toString(expected.getLastOnPath(k), null);
      String a = Objects.toString(actual.getLastOnPath(k), null);
      if (Objects.equals(e, a) == false) {
        diffs++;
        if (first == null) {
          first = k + ": expected " + e + " but loaded table gave " + a;
        }
      }
    }
    assertEquals("answers differ for " + diffs + " of " + probes.size() + " probes; first: " + first,
        0, diffs);
  }
}
