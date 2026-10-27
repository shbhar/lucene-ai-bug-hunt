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
package org.apache.lucene.search.join;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A "from" document that has no value in the join field relates to no "to" document. With
 * multipleValuesPerDocument=false, JoinUtil's term join treats it as if it had the empty term and
 * the numeric join treats it as if it had the value 0, so it joins every "to" document whose join
 * value is the empty string or 0. The same join with multipleValuesPerDocument=true (on the same
 * single-valued field) and the global-ordinals join skip such documents.
 */
public class TestJoinUtilMissingFromValue extends LuceneTestCase {

  /** Term join: from field "customer" (SortedDocValues), to field "id" (indexed term). */
  public void testTermJoinFromDocumentWithoutValue() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      // from side: two orders matching the from query, only one of them has a customer
      w.addDocument(order("o1", "c7", null));
      w.addDocument(order("o2", null, null)); // no join value
      // to side: customers "c7" and "" (an empty but real key)
      w.addDocument(customer("c7", null));
      w.addDocument(customer("", null));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        Query fromQuery = new TermQuery(new Term("type", "order"));
        for (ScoreMode mode : ScoreMode.values()) {
          Query mv = JoinUtil.createJoinQuery("customer", true, "id", fromQuery, s, mode);
          Query sv = JoinUtil.createJoinQuery("customer", false, "id", fromQuery, s, mode);
          assertEquals("multipleValuesPerDocument=true, " + mode, List.of("c7"), ids(s, mv));
          assertEquals("multipleValuesPerDocument=false, " + mode, List.of("c7"), ids(s, sv));
        }
      }
    }
  }

  /** Numeric join: from field "customerNum" (NumericDocValues), to field "num" (IntPoint). */
  public void testNumericJoinFromDocumentWithoutValue() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      w.addDocument(order("o1", null, 7));
      w.addDocument(order("o2", null, null)); // no join value
      w.addDocument(customer("c7", 7));
      w.addDocument(customer("c0", 0)); // customer number 0 is a real key
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        Query fromQuery = new TermQuery(new Term("type", "order"));
        for (ScoreMode mode : ScoreMode.values()) {
          Query mv =
              JoinUtil.createJoinQuery(
                  "customerNum", true, "num", Integer.class, fromQuery, s, mode);
          Query sv =
              JoinUtil.createJoinQuery(
                  "customerNum", false, "num", Integer.class, fromQuery, s, mode);
          assertEquals("multipleValuesPerDocument=true, " + mode, List.of("c7"), ids(s, mv));
          assertEquals("multipleValuesPerDocument=false, " + mode, List.of("c7"), ids(s, sv));
        }
      }
    }
  }

  /**
   * Scores: the from document without a value also contributes its score to the real key 0. Order
   * o1 (customer number 0) scores 1, order o2 (no customer number) scores 5; customer c0 is related
   * to o1 only.
   */
  public void testNumericJoinScoreOfRealZeroKey() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      w.addDocument(order("o1", null, 0));
      w.addDocument(order("o2", null, null)); // no join value
      w.addDocument(customer("c0", 0));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        Query fromQuery =
            new BooleanQuery.Builder()
                .add(constant("o1", 1f), BooleanClause.Occur.SHOULD)
                .add(constant("o2", 5f), BooleanClause.Occur.SHOULD)
                .build();
        for (boolean mvpd : new boolean[] {true, false}) {
          for (ScoreMode mode : new ScoreMode[] {ScoreMode.Max, ScoreMode.Avg, ScoreMode.Total}) {
            Query q =
                JoinUtil.createJoinQuery(
                    "customerNum", mvpd, "num", Integer.class, fromQuery, s, mode);
            TopDocs td = s.search(q, 10);
            assertEquals(1, td.scoreDocs.length);
            assertEquals(
                "multipleValuesPerDocument=" + mvpd + ", " + mode, 1f, td.scoreDocs[0].score, 0f);
          }
        }
      }
    }
  }

  private static Query constant(String id, float score) {
    return new BoostQuery(new ConstantScoreQuery(new TermQuery(new Term("id", id))), score);
  }

  private static Document order(String id, String customer, Integer customerNum) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new StringField("type", "order", Field.Store.NO));
    if (customer != null) {
      d.add(new SortedDocValuesField("customer", new BytesRef(customer)));
    }
    if (customerNum != null) {
      d.add(new NumericDocValuesField("customerNum", customerNum));
    }
    return d;
  }

  private static Document customer(String id, Integer num) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new StringField("type", "customer", Field.Store.NO));
    if (num != null) {
      d.add(new IntPoint("num", num));
    }
    return d;
  }

  private static List<String> ids(IndexSearcher s, Query q) throws Exception {
    List<String> ids = new ArrayList<>();
    for (ScoreDoc sd : s.search(q, 10).scoreDocs) {
      ids.add(s.storedFields().document(sd.doc).get("id"));
    }
    ids.sort(null);
    return ids;
  }
}
