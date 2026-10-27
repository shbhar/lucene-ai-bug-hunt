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
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.egothor.stemmer.Compile;
import org.egothor.stemmer.Gener;
import org.egothor.stemmer.Lift;
import org.egothor.stemmer.MultiTrie2;
import org.egothor.stemmer.Row;
import org.egothor.stemmer.Trie;

/**
 * A stemmer table that went through the Gener reduction cannot be loaded back: Row.store writes the number of all
 * cells but skips the cells that have neither a command nor a reference, which Gener leaves behind. Public API only.
 */
public class TestStoreAfterGener extends LuceneTestCase {

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

  /** 19 forms "Xa" of stem "X" and one form "qb" of stem "qc". */
  private static List<String> smallTable() {
    List<String> lines = new ArrayList<>();
    for (char c : "bcdefghijklmnoprstu".toCharArray()) {
      lines.add(c + " " + c + "a");
    }
    lines.add("qc qb");
    return lines;
  }

  /** Runs the public Compile tool and loads the table it wrote, as StempelStemmer users do. */
  private Trie compileAndLoad(String method, List<String> lines) throws Exception {
    Path dir = createTempDir("stempel");
    Path table = dir.resolve("table.txt");
    Files.write(table, lines, StandardCharsets.UTF_8);
    PrintStream out = System.out;
    System.setOut(new PrintStream(OutputStream.nullOutputStream())); // Compile prints statistics
    try {
      Compile.main(new String[] {method, table.toString()});
    } finally {
      System.setOut(out);
    }
    try (InputStream in = Files.newInputStream(dir.resolve("table.txt.out"))) {
      return StempelStemmer.load(in);
    }
  }

  /** The bundled table's method (-0ME2) followed by Gener: the compiled table must load. */
  public void testPolishTableCompiledWithGenerLoads() throws Exception {
    compileAndLoad("-0ME2", List.of(POLISH)); // control: loads
    Trie t = compileAndLoad("-0ME2G", List.of(POLISH));
    assertNotNull(t);
  }

  /** Smallest Compile case: Lift then Gener on a 20-line table. */
  public void testSmallTableCompiledWithGenerLoads() throws Exception {
    compileAndLoad("-E", smallTable()); // control: loads
    Trie t = compileAndLoad("-EG", smallTable());
    assertEquals("b", new StempelStemmer(t).stem("ba").toString());
  }

  /** Same through the trie API: reduce with Lift then Gener, store, load, compare. */
  public void testStoreLoadRoundTripAfterGener() throws Exception {
    MultiTrie2 trie = new MultiTrie2(false);
    List<String> keys = new ArrayList<>();
    for (char c : "bcdefghijklmnoprstu".toCharArray()) {
      trie.add(c + "a", "Da");
      keys.add(c + "a");
    }
    trie.add("qb", "Rc");
    keys.add("qb");
    Trie reduced = trie.reduce(new Lift(false)).reduce(new Gener());
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream os = new DataOutputStream(bytes)) {
      reduced.store(os);
    }
    Trie loaded = new MultiTrie2(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    for (String k : keys) {
      assertEquals(k, String.valueOf(reduced.getLastOnPath(k)), String.valueOf(loaded.getLastOnPath(k)));
    }
  }

  /** The row itself: a cell whose command was removed (setCmd with -1) is not written but is counted. */
  public void testRowStoreLoadRoundTrip() throws Exception {
    Row row = new Row();
    row.setCmd('a', 0);
    row.setCmd('b', 1);
    row.setCmd('b', -1); // remove the command of 'b', as Gener does
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream os = new DataOutputStream(bytes)) {
      row.store(os);
      os.writeInt(42); // whatever follows the row in a stream
    }
    DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    Row back = new Row(in);
    assertEquals(0, back.getCmd('a'));
    assertEquals("the next value in the stream", 42, in.readInt());
  }
}
