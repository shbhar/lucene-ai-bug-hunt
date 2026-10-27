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
package org.apache.lucene.queries.intervals;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * IndexOptions.DOCS_AND_FREQS: "PhraseQuery and other positional queries will throw an exception".
 * Interval queries built from Intervals.term() do throw on such a field; the same query built from
 * multi-term sources (prefix, wildcard, regexp, fuzzyTerm, range) never checks for positions and
 * silently returns no hits, or, on a MultiReader whose other index has positions, only that index's
 * hits.
 */
public class TestMultiTermIntervalsNoPositionsPerLeaf extends LuceneTestCase {

  private static final Analyzer ANALYZER = new StandardAnalyzer();

  private static Directory index(IndexOptions io, String text) throws IOException {
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setIndexOptions(io);
    ft.freeze();
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(ANALYZER))) {
      Document doc = new Document();
      doc.add(new Field("body", text, ft));
      w.addDocument(doc);
    }
    return dir;
  }

  private static Query termOrdered() {
    return new IntervalQuery("body", Intervals.ordered(Intervals.term("alpha"), Intervals.term("beta")));
  }

  private static Query prefixOrdered() {
    return new IntervalQuery(
        "body",
        Intervals.ordered(
            Intervals.prefix(new BytesRef("alph")), Intervals.prefix(new BytesRef("bet"))));
  }

  /** Control: the term-based query throws on the positions-free field, as documented. */
  public void testTermSourcesThrowWithoutPositions() throws IOException {
    try (Directory d = index(IndexOptions.DOCS_AND_FREQS, "alpha beta");
        DirectoryReader r = DirectoryReader.open(d)) {
      IndexSearcher s = new IndexSearcher(r);
      expectThrows(IllegalArgumentException.class, () -> s.count(termOrdered()));
    }
  }

  /** Control: on a field with positions, the prefix-based query matches the document. */
  public void testPrefixSourcesMatchWithPositions() throws IOException {
    try (Directory d = index(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, "alpha beta");
        DirectoryReader r = DirectoryReader.open(d)) {
      assertEquals(1, new IndexSearcher(r).count(prefixOrdered()));
    }
  }

  /** Single index without positions: expected the same exception, got 0 hits and no error. */
  public void testPrefixSourcesWithoutPositions() throws IOException {
    try (Directory d = index(IndexOptions.DOCS_AND_FREQS, "alpha beta");
        DirectoryReader r = DirectoryReader.open(d)) {
      IndexSearcher s = new IndexSearcher(r);
      assertThrowsIae(s, prefixOrdered());
    }
  }

  /**
   * MultiReader: index 1 has positions, index 2 does not, both hold 'alpha beta'. The term-based
   * query throws on index 2's leaf; the prefix-based query silently returns only index 1's hit.
   */
  public void testPrefixSourcesMultiReaderPartialHits() throws IOException {
    try (Directory d1 = index(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, "alpha beta");
        Directory d2 = index(IndexOptions.DOCS_AND_FREQS, "alpha beta");
        DirectoryReader r1 = DirectoryReader.open(d1);
        DirectoryReader r2 = DirectoryReader.open(d2);
        MultiReader mr = new MultiReader(new IndexReader[] {r1, r2}, false)) {
      IndexSearcher s = new IndexSearcher(mr);
      expectThrows(IllegalArgumentException.class, () -> s.count(termOrdered()));
      assertThrowsIae(s, prefixOrdered());
    }
  }

  /** Positional interval query on a leaf without positions: the documented exception. */
  private static void assertThrowsIae(IndexSearcher s, Query q) throws IOException {
    int count;
    try {
      count = s.count(q);
    } catch (IllegalArgumentException expected) {
      return;
    }
    fail(
        "positional interval query on a field without positions must throw (as Intervals.term"
            + " does), but it silently returned count="
            + count);
  }
}
