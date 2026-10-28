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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A BooleanQuery with one required clause, minimumNumberShouldMatch=1 and no SHOULD clause matches
 * no document: "the specified number of clauses is required" (BooleanQuery.Builder
 * #setMinimumNumberShouldMatch), and core's TestBooleanMinShouldMatch#testNoOptionalButMin2 asserts
 * 0 hits for exactly this shape. A Monitor must therefore never report it.
 *
 * <p>Each check first asserts the IndexSearcher hit count on a one-document index, then that a
 * default Monitor reports the query exactly when IndexSearcher finds a hit. Public API only.
 */
public class TestMonitorMinShouldMatchRequiredClause extends LuceneTestCase {

  private static final String FIELD = "text";
  private static final Analyzer ANALYZER = new StandardAnalyzer();

  private static Query term(String text) {
    return new TermQuery(new Term(FIELD, text));
  }

  /**
   * "required AND at least one of options": one SHOULD clause per option, at least one must match
   */
  private static Query requiredAndAtLeastOneOf(Occur occur, String required, List<String> options) {
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    builder.add(term(required), occur);
    for (String option : options) {
      builder.add(term(option), Occur.SHOULD);
    }
    builder.setMinimumNumberShouldMatch(1);
    return builder.build();
  }

  private static Document doc(String text) {
    Document doc = new Document();
    doc.add(new TextField(FIELD, text, Field.Store.NO));
    return doc;
  }

  private static int indexSearcherCount(Document doc, Query query) throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(ANALYZER))) {
        writer.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        return new IndexSearcher(reader).count(query);
      }
    }
  }

  private static boolean monitorReports(Document doc, Query query) throws IOException {
    try (Monitor monitor = new Monitor(ANALYZER)) { // default configuration and QueryDecomposer
      monitor.register(new MonitorQuery("q", query));
      return monitor.match(doc, QueryMatch.SIMPLE_MATCHER).matches("q") != null;
    }
  }

  private static void assertMonitorAgreesWithIndexSearcher(
      Document doc, Query query, int expectedHits) throws IOException {
    assertEquals("IndexSearcher hits for " + query, expectedHits, indexSearcherCount(doc, query));
    assertEquals(
        "Monitor reports "
            + query
            + " (decomposed to "
            + new QueryDecomposer().decompose(query)
            + ") for a document with "
            + expectedHits
            + " IndexSearcher hits",
        expectedHits > 0,
        monitorReports(doc, query));
  }

  /** (+text:alpha)~1: "alpha AND at least one of []", core's testNoOptionalButMin2 shape. */
  public void testRequiredClauseWithEmptyOptionList() throws IOException {
    Document doc = doc("alpha beta");
    assertMonitorAgreesWithIndexSearcher(
        doc, requiredAndAtLeastOneOf(Occur.MUST, "alpha", List.of()), 0);
  }

  /** (#text:alpha)~1: the same with a FILTER clause. */
  public void testFilterClauseWithEmptyOptionList() throws IOException {
    Document doc = doc("alpha beta");
    assertMonitorAgreesWithIndexSearcher(
        doc, requiredAndAtLeastOneOf(Occur.FILTER, "alpha", List.of()), 0);
  }

  /** (#text:alpha -text:omega)~1: the MUST_NOT exclusion is kept, the "at least one" is not. */
  public void testFilterClauseAndExclusionWithEmptyOptionList() throws IOException {
    Document doc = doc("alpha beta");
    Query query =
        new BooleanQuery.Builder()
            .add(term("alpha"), Occur.FILTER)
            .add(term("omega"), Occur.MUST_NOT)
            .setMinimumNumberShouldMatch(1)
            .build();
    assertMonitorAgreesWithIndexSearcher(doc, query, 0);
  }

  /** text:zzz ((+text:alpha)~1): the bad query nested in a disjunction leaks too. */
  public void testNestedInDisjunction() throws IOException {
    Document doc = doc("alpha beta");
    Query inner = requiredAndAtLeastOneOf(Occur.MUST, "alpha", List.of());
    Query disjunction =
        new BooleanQuery.Builder().add(term("zzz"), Occur.SHOULD).add(inner, Occur.SHOULD).build();
    assertMonitorAgreesWithIndexSearcher(doc, disjunction, 0);
  }

  /** (((+text:alpha)~1) | text:zzz): and in a DisjunctionMaxQuery. */
  public void testNestedInDisjunctionMax() throws IOException {
    Document doc = doc("alpha beta");
    Query inner = requiredAndAtLeastOneOf(Occur.MUST, "alpha", List.of());
    assertMonitorAgreesWithIndexSearcher(
        doc, new DisjunctionMaxQuery(List.of(inner, term("zzz")), 0f), 0);
  }

  /** Controls that pass today: a non-empty option list is handled correctly either way. */
  public void testControls() throws IOException {
    Document doc = doc("alpha beta");
    assertMonitorAgreesWithIndexSearcher(
        doc, requiredAndAtLeastOneOf(Occur.MUST, "alpha", List.of("beta", "gamma")), 1);
    assertMonitorAgreesWithIndexSearcher(
        doc, requiredAndAtLeastOneOf(Occur.MUST, "alpha", List.of("zzz")), 0);
    assertMonitorAgreesWithIndexSearcher(
        doc, new BooleanQuery.Builder().add(term("alpha"), Occur.MUST).build(), 1);
  }
}
