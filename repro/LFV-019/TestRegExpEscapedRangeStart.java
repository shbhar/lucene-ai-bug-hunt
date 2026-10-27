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
package org.apache.lucene.util.automaton;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.automaton.AutomatonTestUtil;

/**
 * A character-class range whose start is backslash-escaped, such as {@code [\.-z]}.
 *
 * <p>The RegExp class javadoc grammar says {@code charclass ::= charexp - charexp} (character
 * range, including end-points) and {@code charexp ::= ... | \ <Unicode character>} (a single
 * character). It also says reserved characters must be escaped, "also in character classes". So
 * {@code [\.-z]} is the range '.'..'z' and must accept the same strings as {@code [.-z]}.
 *
 * <p>Uses public API only (RegExp, Operations, CharacterRunAutomaton, RegexpQuery) plus the
 * test-framework's AutomatonTestUtil.
 */
public class TestRegExpEscapedRangeStart extends LuceneTestCase {

  private static CharacterRunAutomaton run(String regex) {
    Automaton a = new RegExp(regex).toAutomaton();
    return new CharacterRunAutomaton(
        Operations.determinize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT));
  }

  /** Control: an unescaped range start is a range. */
  public void testUnescapedRangeStartControl() {
    CharacterRunAutomaton r = run("[.-z]");
    assertTrue(r.run("a"));
    assertTrue(r.run("/"));
    assertFalse(r.run("-")); // '-' (0x2D) is below '.' (0x2E)
  }

  /** Control: an escaped range end is a range. */
  public void testEscapedRangeEndControl() {
    CharacterRunAutomaton r = run("[a-\\~]"); // regex text: [a-\~]
    assertTrue(r.run("m"));
    assertTrue(r.run("~"));
    assertFalse(r.run("-"));
  }

  /** [\.-z] is the range '.'..'z': it accepts 'a' and '/', and rejects '-'. */
  public void testEscapedRangeStart() {
    String regex = "[\\.-z]"; // regex text: [\.-z]
    CharacterRunAutomaton r = run(regex);
    String tree = new RegExp(regex).toStringTree();
    assertTrue("[\\.-z] must accept 'a'; parse tree:\n" + tree, r.run("a"));
    assertTrue("[\\.-z] must accept '/'; parse tree:\n" + tree, r.run("/"));
    assertFalse("[\\.-z] must reject '-'; parse tree:\n" + tree, r.run("-"));
  }

  /** Escaping the start of a range must not change the language. */
  public void testEscapedRangeStartSameLanguageAsUnescaped() {
    Automaton escaped =
        Operations.determinize(
            new RegExp("[\\.-z]").toAutomaton(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
    Automaton plain =
        Operations.determinize(
            new RegExp("[.-z]").toAutomaton(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
    assertTrue(
        "[\\.-z] and [.-z] must be the same language",
        AutomatonTestUtil.sameLanguage(escaped, plain));
  }

  /** [\!--] is the range '!'..'-' (as [!--] is); it must parse and accept '('. */
  public void testEscapedRangeStartEndingInDash() {
    assertTrue("control: [!--] accepts '('", run("[!--]").run("("));
    CharacterRunAutomaton r = run("[\\!--]"); // regex text: [\!--]
    assertTrue("[\\!--] must accept '('", r.run("("));
  }

  /** End to end: RegexpQuery on [\.-z] must match the same documents as on [.-z]. */
  public void testRegexpQueryEscapedRangeStart() throws IOException {
    String[] values = {"a", "m", "z", ".", "-", "/"}; // all but "-" are in '.'..'z'
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        for (String v : values) {
          Document doc = new Document();
          doc.add(new StringField("f", v, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        assertEquals(5, searcher.count(new RegexpQuery(new Term("f", "[.-z]"))));
        assertEquals(5, searcher.count(new RegexpQuery(new Term("f", "[\\.-z]"))));
      }
    }
  }
}
