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

/**
 * Query#visit must "recurse through the query tree, visiting any child queries", but
 * IndriQuery#visit calls visitor.visitLeaf(this) and never visits its clauses. So
 * QueryVisitor.termCollector finds no terms in an IndriAndQuery, and IndexSearcher's cumulative
 * clause check (TooManyNestedClauses: "more than getMaxClauseCount() total clauses cumulatively in
 * all of its children") counts a whole IndriAndQuery as one clause. Public API only.
 */
public class TestIndriQueryVisitsClauses extends LuceneTestCase {

  private static TermQuery tq(String text) {
    return new TermQuery(new Term("f", text));
  }

  private static IndriAndQuery indriAnd(int from, int to) {
    List<BooleanClause> clauses = new ArrayList<>();
    for (int i = from; i < to; i++) {
      clauses.add(new BooleanClause(tq("t" + i), Occur.SHOULD));
    }
    return new IndriAndQuery(clauses);
  }

  public void testTermCollectorSeesClauseTerms() {
    IndriAndQuery query =
        new IndriAndQuery(
            List.of(
                new BooleanClause(tq("a"), Occur.SHOULD),
                new BooleanClause(tq("b"), Occur.SHOULD)));
    Set<Term> terms = new HashSet<>();
    query.visit(QueryVisitor.termCollector(terms));
    assertEquals(
        "QueryVisitor.termCollector collects all terms that may match a query",
        Set.of(new Term("f", "a"), new Term("f", "b")),
        terms);
  }

  /**
   * RamUsageEstimator.sizeOf(Query) ("the size in bytes of a Query object") walks the query with a
   * QueryVisitor; LRUQueryCache uses it to account the RAM of cached query keys. An IndriAndQuery of
   * 1000 term clauses is accounted as its shallow size, while the BooleanQuery over the same clauses
   * is accounted with its clauses. (LRUQueryCache#getRamBytesUsed calls
   * RamUsageEstimator.sizeOf(query, 32) for a cached query key.)
   */
  public void testQueryRamEstimateCountsIndriClauses() throws Exception {
    List<BooleanClause> clauses = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      clauses.add(new BooleanClause(tq("term-" + i), Occur.SHOULD));
    }
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    clauses.forEach(b::add);
    long boolBytes = org.apache.lucene.util.RamUsageEstimator.sizeOf(b.build());
    long indriBytes = org.apache.lucene.util.RamUsageEstimator.sizeOf(new IndriAndQuery(clauses));

    assertTrue(
        "RamUsageEstimator.sizeOf: BooleanQuery(1000 clauses)="
            + boolBytes
            + " bytes, IndriAndQuery(same 1000 clauses)="
            + indriBytes
            + " bytes",
        indriBytes * 2 >= boolBytes);
  }

  public void testCumulativeClauseLimitCountsIndriClauses() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new StringField("f", "t0", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        int saved = IndexSearcher.getMaxClauseCount();
        IndexSearcher.setMaxClauseCount(4);
        try {
          // Control: two nested BooleanQuery clauses of 3 term clauses each (6 leaves > 4).
          BooleanQuery.Builder inner1 = new BooleanQuery.Builder();
          BooleanQuery.Builder inner2 = new BooleanQuery.Builder();
          for (int i = 0; i < 3; i++) {
            inner1.add(tq("t" + i), Occur.SHOULD);
            inner2.add(tq("t" + (i + 3)), Occur.SHOULD);
          }
          Query nestedBoolean =
              new BooleanQuery.Builder()
                  .add(inner1.build(), Occur.SHOULD)
                  .add(inner2.build(), Occur.SHOULD)
                  .build();
          // (BooleanQuery.rewrite flattens it to one 6-clause BooleanQuery, which throws
          // TooManyClauses; TooManyNestedClauses is a subclass of TooManyClauses.)
          expectThrows(IndexSearcher.TooManyClauses.class, () -> searcher.search(nestedBoolean, 10));

          // Same shape with IndriAndQuery clauses: 6 leaves as well.
          Query nestedIndri =
              new BooleanQuery.Builder()
                  .add(indriAnd(0, 3), Occur.SHOULD)
                  .add(indriAnd(3, 6), Occur.SHOULD)
                  .build();
          IndexSearcher.TooManyClauses e1 = null;
          try {
            searcher.search(nestedIndri, 10);
          } catch (IndexSearcher.TooManyClauses e) {
            e1 = e;
          }
          // A single IndriAndQuery with 51 clauses (IndriAndQuery has no clause check of its own).
          IndexSearcher.TooManyClauses e2 = null;
          int hits = -1;
          try {
            hits = searcher.search(indriAnd(0, 51), 10).scoreDocs.length;
          } catch (IndexSearcher.TooManyClauses e) {
            e2 = e;
          }
          assertTrue(
              "maxClauseCount=4: BooleanQuery(IndriAndQuery(3 clauses), IndriAndQuery(3 clauses))"
                  + " threw TooManyClauses: "
                  + (e1 != null)
                  + "; IndriAndQuery(51 clauses) threw: "
                  + (e2 != null)
                  + " (ran with "
                  + hits
                  + " hits)",
              e1 != null && e2 != null);
        } finally {
          IndexSearcher.setMaxClauseCount(saved);
        }
      }
    }
  }
}
