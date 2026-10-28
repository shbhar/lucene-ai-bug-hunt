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
package org.apache.lucene.monitor;

import java.io.IOException;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KeywordField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Lucene monitor's HighlightsMatch.MATCHER decides whether a stored query matches a document by
 * calling Weight#matches on every document of the batch. With a doc-values-only field queried
 * through DOC_VALUES_REWRITE (SortedSetDocValuesField#newSlowSetQuery), Weight#matches returns null
 * for every document, so (a) a MUST_NOT clause on that field never excludes anything and (b) a
 * required clause on that field never matches. QueryMatch.SIMPLE_MATCHER (scorer-based) gives the
 * correct answer for the same query and document.
 */
public class TestHighlightsMatcherDocValuesDenyClause extends LuceneTestCase {

  private static Document doc(String label, boolean docValuesOnly) {
    Document doc = new Document();
    doc.add(new TextField("body", "a ripe fruit in a basket", Field.Store.NO));
    if (docValuesOnly) {
      doc.add(new SortedSetDocValuesField("label", new BytesRef(label)));
    } else {
      doc.add(new KeywordField("label", label, Field.Store.NO));
    }
    return doc;
  }

  private static Query fruitAnd(Query clause, BooleanClause.Occur occur) {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("body", "fruit")), BooleanClause.Occur.MUST)
        .add(clause, occur)
        .build();
  }

  private static int count(Query q, Document d, MatcherFactory<?> factory, Presearcher presearcher)
      throws IOException {
    try (Monitor monitor = new Monitor(new StandardAnalyzer(), presearcher)) {
      monitor.register(new MonitorQuery("q", q));
      return monitor.match(d, factory).getMatchCount();
    }
  }

  /** Two-document batch (indexed with IndexWriter, not MemoryIndex): match count of document i. */
  private static int countInBatch(Query q, Document[] docs, int i, MatcherFactory<?> factory)
      throws IOException {
    try (Monitor monitor = new Monitor(new StandardAnalyzer())) {
      monitor.register(new MonitorQuery("q", q));
      return monitor.match(docs, factory).getMatchCount(i);
    }
  }

  private static void assertSameAsSimpleMatcher(
      Query q, Document d, int expected, Presearcher presearcher) throws IOException {
    assertEquals(
        "SIMPLE_MATCHER for " + q, expected, count(q, d, QueryMatch.SIMPLE_MATCHER, presearcher));
    assertEquals(
        "HighlightsMatch.MATCHER disagrees with SIMPLE_MATCHER for " + q,
        expected,
        count(q, d, HighlightsMatch.MATCHER, presearcher));
  }

  private static Query denyKeyword() {
    return fruitAnd(
        KeywordField.newSetQuery("label", List.of(new BytesRef("restricted"))),
        BooleanClause.Occur.MUST_NOT);
  }

  private static Query denyDocValuesOnly() {
    return fruitAnd(
        SortedSetDocValuesField.newSlowSetQuery("label", List.of(new BytesRef("restricted"))),
        BooleanClause.Occur.MUST_NOT);
  }

  public void testControlKeywordFieldDenyClause() throws IOException {
    assertSameAsSimpleMatcher(
        denyKeyword(), doc("restricted", false), 0, new TermFilteredPresearcher());
    assertSameAsSimpleMatcher(denyKeyword(), doc("restricted", false), 0, Presearcher.NO_FILTERING);
  }

  public void testDocValuesOnlyDenyClauseFailsOpen() throws IOException {
    // the document carries the denied label, so the stored query must not match it
    assertSameAsSimpleMatcher(
        denyDocValuesOnly(), doc("restricted", true), 0, new TermFilteredPresearcher());
  }

  public void testDocValuesOnlyDenyClauseFailsOpenNoFiltering() throws IOException {
    assertSameAsSimpleMatcher(
        denyDocValuesOnly(), doc("restricted", true), 0, Presearcher.NO_FILTERING);
  }

  public void testDocValuesOnlyDenyClauseFailsOpenInBatch() throws IOException {
    Document[] docs = {doc("public", true), doc("restricted", true)};
    Query q = denyDocValuesOnly();
    assertEquals(1, countInBatch(q, docs, 0, QueryMatch.SIMPLE_MATCHER));
    assertEquals(0, countInBatch(q, docs, 1, QueryMatch.SIMPLE_MATCHER));
    assertEquals(1, countInBatch(q, docs, 0, HighlightsMatch.MATCHER));
    assertEquals(
        "HighlightsMatch.MATCHER disagrees with SIMPLE_MATCHER for the restricted document",
        0,
        countInBatch(q, docs, 1, HighlightsMatch.MATCHER));
  }

  // The default TermFilteredPresearcher cannot select a query whose only usable terms are on a
  // doc-values-only field (the document has no terms there), so these use NO_FILTERING.
  public void testControlKeywordFieldRequiredClause() throws IOException {
    Query q =
        fruitAnd(
            KeywordField.newSetQuery("label", List.of(new BytesRef("public"))),
            BooleanClause.Occur.FILTER);
    assertSameAsSimpleMatcher(q, doc("public", false), 1, Presearcher.NO_FILTERING);
  }

  public void testDocValuesOnlyRequiredClauseFailsClosed() throws IOException {
    Query q =
        fruitAnd(
            SortedSetDocValuesField.newSlowSetQuery("label", List.of(new BytesRef("public"))),
            BooleanClause.Occur.FILTER);
    assertSameAsSimpleMatcher(q, doc("public", true), 1, Presearcher.NO_FILTERING);
  }
}
