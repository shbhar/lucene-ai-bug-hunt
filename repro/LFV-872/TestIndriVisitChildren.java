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
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.RamUsageEstimator;

/**
 * Query#visit: "Recurse through the query tree, visiting any child queries." IndriQuery holds
 * child queries (getClauses()), so a visitor must reach them, as it does for BooleanQuery and
 * DisjunctionMaxQuery over the same children. Public API only (the package is only where Lucene
 * keeps its own search tests).
 */
public class TestIndriVisitChildren extends LuceneTestCase {

  private static final String F = "body";

  private static List<BooleanClause> termClauses(int n) {
    return termClauses(0, n);
  }

  /** SHOULD TermQuery clauses on terms t{from}..t{from+n-1}. */
  private static List<BooleanClause> termClauses(int from, int n) {
    List<BooleanClause> clauses = new ArrayList<>();
    for (int i = from; i < from + n; i++) {
      clauses.add(
          new BooleanClause(new TermQuery(new Term(F, "t" + i)), BooleanClause.Occur.SHOULD));
    }
    return clauses;
  }

  private static BooleanQuery booleanOf(List<BooleanClause> clauses) {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (BooleanClause c : clauses) {
      b.add(c);
    }
    return b.build();
  }

  private static Set<Term> collectTerms(Query q) {
    Set<Term> terms = new HashSet<>();
    q.visit(QueryVisitor.termCollector(terms));
    return terms;
  }

  /** A visitor that records every query it is called for, through every sub-visitor. */
  private static final class RecordingVisitor extends QueryVisitor {
    final List<Query> seen = new ArrayList<>();

    final List<String> log = new ArrayList<>();

    @Override
    public void consumeTerms(Query query, Term... terms) {
      seen.add(query);
      log.add("consumeTerms(" + query.getClass().getSimpleName() + " " + query + ")");
    }

    @Override
    public void visitLeaf(Query query) {
      seen.add(query);
      log.add("visitLeaf(" + query.getClass().getSimpleName() + " " + query + ")");
    }

    @Override
    public QueryVisitor getSubVisitor(BooleanClause.Occur occur, Query parent) {
      return this;
    }
  }

  public void testVisitorReachesChildQueries() {
    List<BooleanClause> clauses = termClauses(2);
    RecordingVisitor boolV = new RecordingVisitor();
    booleanOf(clauses).visit(boolV);
    // control: the BooleanQuery over the same clauses hands both TermQuery children to the visitor
    assertTrue("control " + boolV.log, boolV.seen.contains(clauses.get(0).query()));
    assertTrue("control " + boolV.log, boolV.seen.contains(clauses.get(1).query()));

    IndriAndQuery indri = new IndriAndQuery(clauses);
    RecordingVisitor indriV = new RecordingVisitor();
    indri.visit(indriV);
    assertTrue(
        "Query#visit must visit child queries; the visitor of IndriAndQuery(SHOULD t0, SHOULD t1) got only "
            + indriV.log,
        indriV.seen.contains(clauses.get(0).query())
            && indriV.seen.contains(clauses.get(1).query()));
  }

  public void testTermCollectorSeesIndriClauseTerms() {
    List<BooleanClause> clauses = termClauses(2);
    Set<Term> expected = Set.of(new Term(F, "t0"), new Term(F, "t1"));
    assertEquals("control BooleanQuery", expected, collectTerms(booleanOf(clauses)));
    assertEquals(
        "QueryVisitor.termCollector 'collects all terms that may match a query'",
        expected,
        collectTerms(new IndriAndQuery(clauses)));
  }

  public void testNestedClauseLimitCountsIndriClauses() throws IOException {
    final int saved = IndexSearcher.getMaxClauseCount();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
        Document doc = new Document();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
          sb.append("t").append(i).append(' ');
        }
        doc.add(new Field(F, sb.toString(), TextField.TYPE_NOT_STORED));
        w.addDocument(doc);
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        s.setQueryCache(null);
        IndexSearcher.setMaxClauseCount(8);

        // control 1: a non-Boolean composite (DisjunctionMaxQuery) with 20 term children
        List<Query> dis = new ArrayList<>();
        for (BooleanClause c : termClauses(20)) {
          dis.add(c.query());
        }
        expectThrows(
            IndexSearcher.TooManyNestedClauses.class,
            () -> s.search(new DisjunctionMaxQuery(dis, 0.5f), 10));

        // control 2: two nested BooleanQuery of 6 distinct clauses each under MUST (12 leaves > 8)
        BooleanQuery.Builder nested = new BooleanQuery.Builder();
        nested.add(booleanOf(termClauses(0, 6)), BooleanClause.Occur.MUST);
        nested.add(booleanOf(termClauses(6, 6)), BooleanClause.Occur.MUST);
        BooleanQuery nestedBool = nested.build();
        expectThrows(IndexSearcher.TooManyClauses.class, () -> s.search(nestedBool, 10));

        // same shapes with IndriAndQuery
        IndriAndQuery big = new IndriAndQuery(termClauses(20));
        Throwable t1 = null;
        try {
          TopDocs td = s.search(big, 10);
          if (VERBOSE) System.out.println("IndriAndQuery(20) ran: " + td.totalHits);
        } catch (IndexSearcher.TooManyClauses e) {
          t1 = e;
        }
        BooleanQuery.Builder nestedIndri = new BooleanQuery.Builder();
        nestedIndri.add(new IndriAndQuery(termClauses(0, 6)), BooleanClause.Occur.MUST);
        nestedIndri.add(new IndriAndQuery(termClauses(6, 6)), BooleanClause.Occur.MUST);
        Throwable t2 = null;
        try {
          s.search(nestedIndri.build(), 10);
        } catch (IndexSearcher.TooManyClauses e) {
          t2 = e;
        }
        assertTrue(
            "maxClauseCount=8: TooManyNestedClauses is for queries with 'more than getMaxClauseCount()"
                + " total clauses cumulatively in all of its children'; IndriAndQuery(20 clauses) threw="
                + (t1 != null)
                + ", BooleanQuery(MUST IndriAndQuery(6), MUST IndriAndQuery(6)) threw="
                + (t2 != null),
            t1 != null && t2 != null);
      }
    } finally {
      IndexSearcher.setMaxClauseCount(saved);
    }
  }

  public void testRamEstimateCountsIndriClauses() {
    List<BooleanClause> clauses = termClauses(500);
    long bool = RamUsageEstimator.sizeOf(booleanOf(clauses));
    long indri = RamUsageEstimator.sizeOf(new IndriAndQuery(clauses));
    long bool32 = RamUsageEstimator.sizeOf(booleanOf(clauses), 32);
    long indri32 = RamUsageEstimator.sizeOf(new IndriAndQuery(clauses), 32);
    String msg =
        "RamUsageEstimator.sizeOf over 500 term clauses: BooleanQuery="
            + bool
            + " IndriAndQuery="
            + indri
            + "; with defSize 32 (the query cache's key accounting): BooleanQuery="
            + bool32
            + " IndriAndQuery="
            + indri32;
    System.out.println(msg);
    assertTrue(msg, indri * 2 >= bool && indri32 * 2 >= bool32);
  }
}
