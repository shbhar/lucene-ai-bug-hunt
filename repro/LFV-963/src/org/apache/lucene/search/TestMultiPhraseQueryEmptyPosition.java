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
package org.apache.lucene.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * MultiPhraseQuery.Builder#add(Term[]) documents the array as a disjunction ("Any of the terms may
 * match"). An empty array is an empty disjunction. The Builder accepts it at every position except
 * the first, where it throws ArrayIndexOutOfBoundsException; the queries it accepts match nothing
 * (as an empty disjunction should) but cannot be printed, so toString() and explain() throw
 * ArrayIndexOutOfBoundsException.
 */
public class TestMultiPhraseQueryEmptyPosition extends LuceneTestCase {

  private static final Term[] EMPTY = new Term[0];

  /** Returns "accepted" or the simple class name of what add(EMPTY) threw. */
  private static String addEmpty(MultiPhraseQuery.Builder builder) {
    try {
      builder.add(EMPTY);
      return "accepted";
    } catch (RuntimeException e) {
      return e.getClass().getSimpleName();
    }
  }

  /**
   * Builds a query with an empty later position. If the Builder rejects empty arrays with
   * IllegalArgumentException (one acceptable fix), the remaining legs do not apply.
   */
  private static MultiPhraseQuery buildWithEmptyPosition(Term... before) {
    MultiPhraseQuery.Builder b = new MultiPhraseQuery.Builder();
    for (Term t : before) {
      b.add(t);
    }
    try {
      b.add(EMPTY);
    } catch (IllegalArgumentException e) {
      assumeTrue("the Builder rejects empty arrays: " + e.getMessage(), false);
    }
    return b.build();
  }

  /**
   * The Builder must treat an empty array the same way at every position: either accept it, or
   * reject it with IllegalArgumentException (as it rejects other invalid input), never with
   * ArrayIndexOutOfBoundsException.
   */
  public void testEmptyArrayHandledTheSameAtEveryPosition() {
    String atFirstPosition = addEmpty(new MultiPhraseQuery.Builder());
    String atSecondPosition = addEmpty(new MultiPhraseQuery.Builder().add(new Term("body", "a")));
    assertTrue(
        "first position: " + atFirstPosition,
        atFirstPosition.equals("accepted") || atFirstPosition.equals("IllegalArgumentException"));
    assertEquals(
        "empty array at the first position vs the second position",
        atSecondPosition,
        atFirstPosition);
  }

  /** A query the Builder accepted must be printable. */
  public void testToStringWithEmptyLaterPosition() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
        for (String text : new String[] {"a b c", "a c", "a x c"}) {
          Document doc = new Document();
          doc.add(new TextField("body", text, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        MultiPhraseQuery q =
            new MultiPhraseQuery.Builder(buildWithEmptyPosition(new Term("body", "a")))
                .add(new Term("body", "c"))
                .build();
        // search treats the empty position as an empty disjunction: nothing matches
        assertEquals(0, searcher.count(q));
        // but the query cannot be printed
        assertNotNull(q.toString());
        assertNotNull(q.toString("body"));
      }
    }
  }

  /**
   * explain() of a BooleanQuery prints every clause that does not match, so one accepted
   * MultiPhraseQuery clause makes explain() throw for every hit of the BooleanQuery.
   */
  public void testExplainOfBooleanQueryWithEmptyPositionClause() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
        for (String text : new String[] {"a b c", "a c", "b c"}) {
          Document doc = new Document();
          doc.add(new TextField("body", text, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        // "b (nothing)": e.g. a prefix expansion for the second word that found no terms
        MultiPhraseQuery phrase = buildWithEmptyPosition(new Term("body", "b"));
        BooleanQuery bq =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("body", "a")), BooleanClause.Occur.SHOULD)
                .add(phrase, BooleanClause.Occur.SHOULD)
                .build();
        TopDocs hits = searcher.search(bq, 10);
        assertEquals(2, hits.scoreDocs.length);
        for (ScoreDoc sd : hits.scoreDocs) {
          Explanation e = searcher.explain(bq, sd.doc);
          assertTrue(e.isMatch());
        }
      }
    }
  }

  /**
   * explain() of a BooleanQuery prints a required clause that does not match, so asking why a
   * document does not match a BooleanQuery with such a required clause throws.
   */
  public void testExplainOfRequiredEmptyPositionClause() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
        Document doc = new Document();
        doc.add(new TextField("body", "a b c", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        MultiPhraseQuery phrase = buildWithEmptyPosition(new Term("body", "b"));
        BooleanQuery bq =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("body", "a")), BooleanClause.Occur.MUST)
                .add(phrase, BooleanClause.Occur.MUST)
                .build();
        assertEquals(0, searcher.count(bq));
        Explanation e = searcher.explain(bq, 0);
        assertFalse(e.isMatch());
      }
    }
  }

  /**
   * On a field indexed without positions, MultiPhraseQuery throws IllegalStateException ("was
   * indexed without position data"); its message prints the query, so with an accepted empty
   * position the user gets ArrayIndexOutOfBoundsException from toString() instead.
   */
  public void testPositionlessFieldError() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
        Document doc = new Document();
        doc.add(new StringField("id", "a", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        MultiPhraseQuery control =
            new MultiPhraseQuery.Builder().add(new Term("id", "a")).add(new Term("id", "b")).build();
        IllegalStateException expected =
            expectThrows(IllegalStateException.class, () -> searcher.count(control));
        assertTrue(expected.getMessage(), expected.getMessage().contains("without position data"));

        MultiPhraseQuery q = buildWithEmptyPosition(new Term("id", "a"));
        // matching nothing (0) or the same IllegalStateException are both fine; another exception
        // type is not
        try {
          assertEquals(0, searcher.count(q));
        } catch (IllegalStateException e) {
          assertTrue(e.getMessage(), e.getMessage().contains("without position data"));
        }
      }
    }
  }
}
