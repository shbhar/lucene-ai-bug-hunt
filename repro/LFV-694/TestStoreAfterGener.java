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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.lucene.analysis.stempel.StempelStemmer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Row.store writes the number of all cells of a row but skips the empty ones (no command and no
 * reference), which Gener leaves behind; Row(DataInput) trusts the number. Public API only.
 */
public class TestStoreAfterGener extends LuceneTestCase {

  /** A row with one emptied cell, followed by another row in the same stream. */
  public void testRowWithEmptyCellRoundTrip() throws Exception {
    Row first = new Row();
    first.setCmd('a', 0);
    first.setCmd('b', 1);
    first.setCmd('b', -1); // remove the command again: the cell has no command and no reference
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
    assertEquals("cells in use in the first row", first.getCells(), first2.getCells());
    assertEquals("command of 'x' in the second row", 3, second2.getCmd('x'));
  }

  /**
   * Gener drops the 20 forms "aca" .. "act" (each is 1 of 20 in its row), which empties the cell
   * 'c' of the row that holds "ab". That row is the last one in the file.
   */
  public void testGenerTableLoads() throws Exception {
    List<String> dict = List.of("a ab", "ac" + forms("ac"));
    assertEquals("a", stem(compileAndLoad("X", dict), "ab")); // control: no reduction letter
    assertEquals("a", stem(compileAndLoad("G", dict), "ab")); // Gener
  }

  /**
   * Gener drops the 20 forms "xa" .. "xt", which empties the root cell 'x'. Another row (the one
   * that holds "ab") follows the root in the file.
   */
  public void testGenerTableStemsLikeStoredTrie() throws Exception {
    List<String> dict = List.of("x" + forms("x"), "a ab");
    assertEquals("a", stem(compileAndLoad("X", dict), "ab")); // control: no reduction letter
    assertEquals("a", stem(compileAndLoad("G", dict), "ab")); // Gener
  }

  /** " sa sb ... st": 20 forms of the stem s, each used by one word only. */
  private static String forms(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c = 'a'; c < 'a' + 20; c++) {
      sb.append(' ').append(s).append(c);
    }
    return sb.toString();
  }

  private static String stem(Trie trie, String word) {
    StringBuilder stem = new StempelStemmer(trie).stem(word);
    return stem == null ? null : stem.toString();
  }

  /** Runs the Compile tool as a user would and loads the table it wrote. */
  private Trie compileAndLoad(String method, List<String> dict) throws Exception {
    Path dir = createTempDir("stempel");
    Path table = dir.resolve("dict.txt");
    Files.write(table, dict, StandardCharsets.UTF_8);
    PrintStream stdout = System.out;
    System.setOut(new PrintStream(OutputStream.nullOutputStream())); // Compile prints statistics
    try {
      Compile.main(new String[] {method, table.toString()});
    } finally {
      System.setOut(stdout);
    }
    try (InputStream in = Files.newInputStream(dir.resolve("dict.txt.out"))) {
      return StempelStemmer.load(in);
    }
  }
}
