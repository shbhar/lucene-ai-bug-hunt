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
package org.apache.lucene.facet.range;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.MultiDoubleValuesSource;
import org.apache.lucene.facet.MultiLongValuesSource;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * LongRange/DoubleRange#getQuery(fastMatchQuery, valueSource) runs fastMatchQuery as its filter,
 * but the returned query's visit() never reaches it and its Weight is built without
 * IndexSearcher#rewrite, so IndexSearcher's clause limit never checks the fast-match query at all.
 * maxClauseCount is the default 1024; each fast-match query holds 1200 term clauses.
 */
public class TestRangeQueryFastMatchClauseLimit extends LuceneTestCase {

  private static Query disjunction(String prefix, int n) {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (int i = 0; i < n; i++) {
      b.add(new TermQuery(new Term("f", prefix + i)), Occur.SHOULD);
    }
    return b.build();
  }

  /** 1200 term clauses: two required disjunctions of 600 (no single BooleanQuery exceeds 1024). */
  private static Query bigFastMatch() {
    return new BooleanQuery.Builder()
        .add(disjunction("a", 600), Occur.MUST)
        .add(disjunction("b", 600), Occur.MUST)
        .build();
  }

  private interface Body {
    void run(IndexSearcher searcher) throws Exception;
  }

  private void withSearcher(Body body) throws Exception {
    assertEquals(1024, IndexSearcher.getMaxClauseCount());
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 20; i++) {
        Document doc = new Document();
        doc.add(new StringField("f", "a" + i, Field.Store.NO));
        doc.add(new StringField("f", "b" + i, Field.Store.NO));
        doc.add(new NumericDocValuesField("n", i));
        doc.add(new SortedNumericDocValuesField("sn", i));
        doc.add(new SortedNumericDocValuesField("sn", i + 100));
        w.addDocument(doc);
      }
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        // control: the same fast-match query as a FILTER clause next to the range is rejected
        Query control =
            new BooleanQuery.Builder()
                .add(bigFastMatch(), Occur.FILTER)
                .add(
                    new LongRange("r", 0, true, 1000, true)
                        .getQuery(null, LongValuesSource.fromLongField("n")),
                    Occur.FILTER)
                .build();
        expectThrows(IndexSearcher.TooManyNestedClauses.class, () -> searcher.search(control, 10));
        body.run(searcher);
      }
    }
  }

  private static void assertRejected(IndexSearcher searcher, Query query) {
    expectThrows(IndexSearcher.TooManyClauses.class, () -> searcher.search(query, 10));
  }

  public void testLongRangeFastMatchIsCheckedAgainstClauseLimit() throws Exception {
    withSearcher(
        searcher ->
            assertRejected(
                searcher,
                new LongRange("r", 0, true, 1000, true)
                    .getQuery(bigFastMatch(), LongValuesSource.fromLongField("n"))));
  }

  public void testDoubleRangeFastMatchIsCheckedAgainstClauseLimit() throws Exception {
    withSearcher(
        searcher ->
            assertRejected(
                searcher,
                new DoubleRange("r", 0, true, 1000, true)
                    .getQuery(bigFastMatch(), DoubleValuesSource.fromLongField("n"))));
  }

  public void testMultiValuedLongRangeFastMatchIsCheckedAgainstClauseLimit() throws Exception {
    withSearcher(
        searcher ->
            assertRejected(
                searcher,
                new LongRange("r", 0, true, 1000, true)
                    .getQuery(bigFastMatch(), MultiLongValuesSource.fromLongField("sn"))));
  }

  public void testMultiValuedDoubleRangeFastMatchIsCheckedAgainstClauseLimit() throws Exception {
    withSearcher(
        searcher ->
            assertRejected(
                searcher,
                new DoubleRange("r", 0, true, 1000, true)
                    .getQuery(bigFastMatch(), MultiDoubleValuesSource.fromLongField("sn"))));
  }
}
