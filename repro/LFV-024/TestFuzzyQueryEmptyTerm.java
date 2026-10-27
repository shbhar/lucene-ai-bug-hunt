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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.ByteRunAutomaton;

/**
 * FuzzyQuery whose term text is the empty string.
 *
 * <p>The FuzzyQuery constructor javadoc says the query "will match terms with an edit distance of
 * at most maxEdits to term", and CHANGES (LUCENE-7439, 6.3.0) says "FuzzyQuery now matches all
 * terms within the specified edit distance, even if they are short terms".
 *
 * <p>The edit distance from "" to a term is the term's length in code points (the term can only be
 * built from "" by insertions). With the indexed terms below:
 *
 * <pre>
 *   term        ""  "a"  "b"  "ab"  "abc"  "x"
 *   from ""      0    1    1    2     3     1
 *   from "a"     1    0    1    1     2     1
 * </pre>
 *
 * Uses public API only.
 */
public class TestFuzzyQueryEmptyTerm extends LuceneTestCase {

  private static final String FIELD = "f";
  private static final String[] TERMS = {"", "a", "b", "ab", "abc", "x"};

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String t : TERMS) {
        Document doc = new Document();
        doc.add(new StringField(FIELD, t, Field.Store.YES));
        w.addDocument(doc);
      }
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  /** The terms of the matching documents, sorted and quoted, e.g. ["", "a"]. */
  private String matchingTerms(Query query) throws IOException {
    Set<String> result = new TreeSet<>();
    StoredFields storedFields = searcher.storedFields();
    for (ScoreDoc hit : searcher.search(query, TERMS.length).scoreDocs) {
      result.add(storedFields.document(hit.doc).get(FIELD));
    }
    return quoted(result);
  }

  /** Quotes each term so that the empty term shows as "" in failure messages. */
  private static String quoted(Set<String> terms) {
    List<String> out = new ArrayList<>();
    for (String t : new TreeSet<>(terms)) {
      out.add('"' + t + '"');
    }
    return out.toString();
  }

  private static String set(String... terms) {
    return quoted(Set.of(terms));
  }

  // ---- the bug: these four assertions fail on main ----

  public void testEmptyTermMaxEdits1() throws IOException {
    Query q = new FuzzyQuery(new Term(FIELD, ""), 1);
    assertEquals(set("", "a", "b", "x"), matchingTerms(q));
  }

  public void testEmptyTermMaxEdits2() throws IOException {
    Query q = new FuzzyQuery(new Term(FIELD, ""), 2);
    assertEquals(set("", "a", "ab", "b", "x"), matchingTerms(q));
  }

  /** Not a scoring or top-N effect: a constant-score rewrite drops the same terms. */
  public void testEmptyTermConstantScoreRewrite() throws IOException {
    Query q =
        new FuzzyQuery(new Term(FIELD, ""), 2, 0, 50, true, MultiTermQuery.CONSTANT_SCORE_REWRITE);
    assertEquals(set("", "a", "ab", "b", "x"), matchingTerms(q));
  }

  /** The public FuzzyTermsEnum constructor, whose javadoc promises "at most maxEdits edits". */
  public void testFuzzyTermsEnumEmptyTerm() throws IOException {
    FuzzyTermsEnum te =
        new FuzzyTermsEnum(MultiTerms.getTerms(reader, FIELD), new Term(FIELD, ""), 2, 0, true);
    Set<String> enumerated = new TreeSet<>();
    for (BytesRef b = te.next(); b != null; b = te.next()) {
      enumerated.add(b.utf8ToString());
    }
    assertEquals(set("", "a", "ab", "b", "x"), quoted(enumerated));
  }

  // ---- controls: these pass on main ----

  /** A one-character query term matches every term within maxEdits, including "". */
  public void testControlOneCharTerm() throws IOException {
    assertEquals(
        set("", "a", "ab", "b", "x"), matchingTerms(new FuzzyQuery(new Term(FIELD, "a"), 1)));
    assertEquals(
        set("", "a", "ab", "abc", "b", "x"),
        matchingTerms(new FuzzyQuery(new Term(FIELD, "a"), 2)));
  }

  /** The automaton that FuzzyQuery builds for "" (also what visit() reports) is correct. */
  public void testControlAutomatonForEmptyTerm() {
    ByteRunAutomaton automaton =
        new FuzzyQuery(new Term(FIELD, ""), 2).getAutomata().runAutomaton;
    Set<String> accepted = new TreeSet<>();
    for (String t : TERMS) {
      BytesRef b = new BytesRef(t);
      if (automaton.run(b.bytes, b.offset, b.length)) {
        accepted.add(t);
      }
    }
    assertEquals(set("", "a", "ab", "b", "x"), quoted(accepted));
  }
}
