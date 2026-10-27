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
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The Kleene star of the empty language.
 *
 * <p>For every language L, L* contains the empty string (zero repetitions), so the star of the
 * empty language is {""}. {@link Operations#repeat(Automaton)} is documented as "the Kleene star
 * (zero or more concatenated repetitions)", {@link Operations#repeat(Automaton, int)} as "min or
 * more concatenated repetitions", and the RegExp grammar documents {@code *} as "zero or more
 * occurrences", {@code {n,}} as "n or more occurrences" and {@code #} as "the empty language".
 *
 * <p>Uses public API only (Automata, Automaton, Operations, RegExp, RegexpQuery, IndexWriter).
 */
public class TestKleeneStarOfEmptyLanguage extends LuceneTestCase {

  private static boolean accepts(Automaton a, String s) {
    // Operations.run needs a deterministic automaton.
    return Operations.run(Operations.determinize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT), s);
  }

  private static boolean regexpAccepts(String regexp, String s) {
    return accepts(new RegExp(regexp).toAutomaton(), s);
  }

  /** Control (passes): optional and bounded repeat of the empty language accept "". */
  public void testControlOptionalAndBoundedRepeatOfEmptyLanguage() {
    assertTrue(accepts(Operations.optional(Automata.makeEmpty()), ""));
    assertTrue(accepts(Operations.repeat(Automata.makeEmpty(), 0, 3), ""));
    assertTrue(regexpAccepts("#?", ""));
    assertTrue(regexpAccepts("(#){0,5}", ""));
    assertTrue(regexpAccepts("(a&b)?", ""));
  }

  public void testRepeatOfEmptyLanguageAcceptsEmptyString() {
    Automaton star = Operations.repeat(Automata.makeEmpty());
    assertTrue("repeat(makeEmpty()) must accept \"\" (zero repetitions)", accepts(star, ""));
  }

  public void testRepeatMinZeroOfEmptyLanguageAcceptsEmptyString() {
    Automaton star = Operations.repeat(Automata.makeEmpty(), 0);
    assertTrue("repeat(makeEmpty(), 0) must accept \"\" (zero repetitions)", accepts(star, ""));
  }

  /** Two automata with the same (empty) language must have stars with the same language. */
  public void testRepeatDependsOnlyOnTheLanguage() {
    Automaton zeroStates = Automata.makeEmpty();
    Automaton oneDeadState = new Automaton();
    oneDeadState.createState(); // one state, not accepting, no transitions
    oneDeadState.finishState();
    assertEquals(0, zeroStates.getNumStates());
    assertEquals(1, oneDeadState.getNumStates());
    assertTrue(Operations.isEmpty(zeroStates));
    assertTrue(Operations.isEmpty(oneDeadState));

    boolean oneStateStar = accepts(Operations.repeat(oneDeadState), "");
    boolean zeroStateStar = accepts(Operations.repeat(zeroStates), "");
    assertTrue("control: the star of the 1-state empty automaton accepts \"\"", oneStateStar);
    assertEquals(
        "the star of the empty language depends on how the empty language is represented",
        oneStateStar,
        zeroStateStar);
  }

  public void testRegExpStarOfEmptySubexpression() {
    // Each of these must accept "" (zero occurrences).
    assertEquals(
        "accepts \"\"",
        "#*=true (a&b)*=true (#){0,}=true",
        "#*="
            + regexpAccepts("#*", "")
            + " (a&b)*="
            + regexpAccepts("(a&b)*", "")
            + " (#){0,}="
            + regexpAccepts("(#){0,}", ""));
  }

  /** Concatenation with the wrongly empty star empties the whole expression. */
  public void testStarOfEmptySubexpressionEmptiesTheWholeRegExp() {
    assertTrue("control: 'a(a&b)?' accepts \"a\"", regexpAccepts("a(a&b)?", "a"));
    assertTrue("'a(a&b)*' must accept \"a\"", regexpAccepts("a(a&b)*", "a"));
  }

  /** End to end: RegexpQuery (default flags RegExp.ALL) over the terms "" and "a". */
  public void testRegexpQueryMissesMatchingTerms() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String term : new String[] {"", "a"}) {
        Document doc = new Document();
        doc.add(new StringField("f", term, Field.Store.NO));
        w.addDocument(doc);
      }
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        assertEquals("control: 'a*'", 2, s.count(new RegexpQuery(new Term("f", "a*"))));
        assertEquals("control: '(a&b)?'", 1, s.count(new RegexpQuery(new Term("f", "(a&b)?"))));
        assertEquals("control: 'a(a&b)?'", 1, s.count(new RegexpQuery(new Term("f", "a(a&b)?"))));
        int star = s.count(new RegexpQuery(new Term("f", "(a&b)*")));
        int aStar = s.count(new RegexpQuery(new Term("f", "a(a&b)*")));
        // '(a&b)*' must match the empty term and 'a(a&b)*' must match the term "a".
        assertEquals(
            "hit counts of '(a&b)*' and 'a(a&b)*'",
            "(a&b)*=1 a(a&b)*=1",
            "(a&b)*=" + star + " a(a&b)*=" + aStar);
      }
    }
  }
}
