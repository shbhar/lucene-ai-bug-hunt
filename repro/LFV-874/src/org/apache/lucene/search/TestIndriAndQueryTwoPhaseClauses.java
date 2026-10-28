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

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndriAndQuery "matches documents matching combinations of subqueries", but when two or more of
 * its clauses have a scorer in a segment, IndriDisjunctionScorer iterates the disjunction of the
 * clauses' approximations and offers no TwoPhaseIterator, so a clause that uses two-phase
 * iteration (PhraseQuery, doc-values range queries, ...) matches every document of its
 * approximation. Public API only (SHOULD clauses only).
 *
 * <p>Docs: 0 {body="a b", n=1}, 1 {body="b a", n=50}, 2 {body="c", n=2}, 3 {body="x", n=100}.
 */
public class TestIndriAndQueryTwoPhaseClauses extends LuceneTestCase {

  private static Set<Integer> hits(IndexSearcher s, Query q) throws Exception {
    Set<Integer> out = new TreeSet<>();
    for (ScoreDoc sd : s.search(q, 10).scoreDocs) {
      out.add(sd.doc);
    }
    return out;
  }

  public void testHitsMatchAtLeastOneSubquery() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
        String[] bodies = {"a b", "b a", "c", "x"};
        long[] values = {1, 50, 2, 100};
        for (int i = 0; i < bodies.length; i++) {
          Document doc = new Document();
          doc.add(new TextField("body", bodies[i], Field.Store.NO));
          doc.add(new SortedNumericDocValuesField("n", values[i]));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        Query c = new TermQuery(new Term("body", "c"));
        Query[] twoPhaseClauses = {
          new PhraseQuery("body", "a", "b"), // matches doc 0 only
          SortedNumericDocValuesField.newSlowRangeQuery("n", 1, 10) // matches docs 0 and 2
        };
        StringBuilder report = new StringBuilder();
        for (Query tp : twoPhaseClauses) {
          List<BooleanClause> clauses =
              Arrays.asList(new BooleanClause(tp, Occur.SHOULD), new BooleanClause(c, Occur.SHOULD));
          Set<Integer> expected = new TreeSet<>(hits(searcher, tp));
          expected.addAll(hits(searcher, c));
          Query indri = new IndriAndQuery(clauses);
          Set<Integer> actual = hits(searcher, indri);
          int count = searcher.count(indri);
          if (actual.equals(expected) == false || count != expected.size()) {
            report
                .append("\n  ")
                .append(indri)
                .append(": hits ")
                .append(actual)
                .append(", count ")
                .append(count)
                .append("; documents matching a subquery: ")
                .append(expected);
          }
        }
        assertEquals(
            "every hit of an IndriAndQuery must match at least one of its subqueries:",
            "",
            report.toString());
      }
    }
  }
}
