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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.RamUsageEstimator;

/**
 * Query#visit must "recurse through the query tree, visiting any child queries". IndriQuery#visit
 * only calls visitor.visitLeaf(this), so no QueryVisitor ever sees the clauses of an IndriAndQuery.
 * Each test compares an IndriAndQuery with a BooleanQuery (or DisjunctionMaxQuery) over the same
 * TermQuery clauses. Public API only.
 */
public class TestIndriAndQueryVisitsClauses extends LuceneTestCase {

  private static final String FIELD = "body";

  private static List<BooleanClause> shouldClauses(String... terms) {
    List<BooleanClause> clauses = new ArrayList<>();
    for (String t : terms) {
      clauses.add(new BooleanClause(new TermQuery(new Term(FIELD, t)), Occur.SHOULD));
    }
    return clauses;
  }

  private static List<BooleanClause> numberedClauses(int from, int to) {
    List<BooleanClause> clauses = new ArrayList<>();
    for (int i = from; i < to; i++) {
      clauses.add(new BooleanClause(new TermQuery(new Term(FIELD, "t" + i)), Occur.SHOULD));
    }
    return clauses;
  }

  private static BooleanQuery booleanOf(List<BooleanClause> clauses) {
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (BooleanClause c : clauses) {
      builder.add(c);
    }
    return builder.build();
  }

  private static Set<Term> collectTerms(Query query) {
    Set<Term> terms = new HashSet<>();
    query.visit(QueryVisitor.termCollector(terms));
    return terms;
  }

  /** QueryVisitor.termCollector "collects all terms that may match a query". */
  public void testTermCollectorSeesClauseTerms() {
    List<BooleanClause> clauses = shouldClauses("president", "washington");
    Set<Term> expected = Set.of(new Term(FIELD, "president"), new Term(FIELD, "washington"));
    assertEquals("control: BooleanQuery", expected, collectTerms(booleanOf(clauses)));
    assertEquals(
        "termCollector over IndriAndQuery(body:president, body:washington)",
        expected,
        collectTerms(new IndriAndQuery(clauses)));
  }

  /**
   * IndexSearcher.TooManyNestedClauses: "a Query that has more than getMaxClauseCount() total
   * clauses cumulatively in all of its children".
   */
  public void testCumulativeClauseLimitCountsIndriClauses() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new StringField(FIELD, "t0", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        int saved = IndexSearcher.getMaxClauseCount();
        IndexSearcher.setMaxClauseCount(4);
        try {
          List<Query> six = new ArrayList<>();
          for (BooleanClause c : numberedClauses(0, 6)) {
            six.add(c.query());
          }
          // Controls: other composites over the same 6 term clauses are refused.
          expectThrows(
              IndexSearcher.TooManyClauses.class,
              () -> searcher.search(new DisjunctionMaxQuery(six, 0f), 10));
          Query nestedBoolean =
              new BooleanQuery.Builder()
                  .add(booleanOf(numberedClauses(0, 3)), Occur.MUST)
                  .add(booleanOf(numberedClauses(3, 6)), Occur.MUST)
                  .build();
          expectThrows(
              IndexSearcher.TooManyClauses.class, () -> searcher.search(nestedBoolean, 10));

          // The same 6 term clauses inside IndriAndQuery.
          Query nestedIndri =
              new BooleanQuery.Builder()
                  .add(new IndriAndQuery(numberedClauses(0, 3)), Occur.SHOULD)
                  .add(new IndriAndQuery(numberedClauses(3, 6)), Occur.SHOULD)
                  .build();
          String flat = outcome(searcher, new IndriAndQuery(numberedClauses(0, 6)));
          String nested = outcome(searcher, nestedIndri);
          assertTrue(
              "maxClauseCount=4, the DisjunctionMaxQuery and nested BooleanQuery controls throw;"
                  + " IndriAndQuery(6 clauses): "
                  + flat
                  + "; BooleanQuery(IndriAndQuery(3 clauses), IndriAndQuery(3 clauses)): "
                  + nested,
              flat.startsWith("threw") && nested.startsWith("threw"));
        } finally {
          IndexSearcher.setMaxClauseCount(saved);
        }
      }
    }
  }

  private static String outcome(IndexSearcher searcher, Query query) throws IOException {
    try {
      return "ran, hits=" + searcher.search(query, 10).scoreDocs.length;
    } catch (IndexSearcher.TooManyClauses e) {
      return "threw " + e.getClass().getSimpleName();
    }
  }

  /**
   * RamUsageEstimator.sizeOf(Query) walks the query with a QueryVisitor (LRUQueryCache on main uses
   * it for cached query keys). A consequence of the same root: the estimate ignores the clauses.
   */
  public void testRamEstimateDependsOnClauses() {
    long indri1 = RamUsageEstimator.sizeOf(new IndriAndQuery(numberedClauses(0, 1)));
    long indri1000 = RamUsageEstimator.sizeOf(new IndriAndQuery(numberedClauses(0, 1000)));
    long bool1000 = RamUsageEstimator.sizeOf(booleanOf(numberedClauses(0, 1000)));
    assertTrue(
        "RamUsageEstimator.sizeOf: IndriAndQuery(1 clause)="
            + indri1
            + " bytes, IndriAndQuery(1000 clauses)="
            + indri1000
            + " bytes, BooleanQuery(same 1000 clauses)="
            + bool1000
            + " bytes",
        indri1000 > indri1);
  }
}
