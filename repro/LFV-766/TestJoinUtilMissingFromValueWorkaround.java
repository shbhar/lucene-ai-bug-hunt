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
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Workaround check on stock Lucene: restricting fromQuery to documents that have the from field
 * (FieldExistsQuery as a FILTER clause) makes the single-valued joins return the right answer.
 */
public class TestJoinUtilMissingFromValueWorkaround extends LuceneTestCase {

  public void testTermJoinWithFieldExistsFilter() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      w.addDocument(order("o1", "c7", null));
      w.addDocument(order("o2", null, null));
      w.addDocument(customer("c7", null));
      w.addDocument(customer("", null));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        Query fromQuery = withExists(new TermQuery(new Term("type", "order")), "customer");
        for (ScoreMode mode : ScoreMode.values()) {
          Query sv = JoinUtil.createJoinQuery("customer", false, "id", fromQuery, s, mode);
          assertEquals("multipleValuesPerDocument=false, " + mode, List.of("c7"), ids(s, sv));
        }
      }
    }
  }

  public void testNumericJoinWithFieldExistsFilter() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      w.addDocument(order("o1", null, 7));
      w.addDocument(order("o2", null, null));
      w.addDocument(customer("c7", 7));
      w.addDocument(customer("c0", 0));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        Query fromQuery = withExists(new TermQuery(new Term("type", "order")), "customerNum");
        for (ScoreMode mode : ScoreMode.values()) {
          Query sv =
              JoinUtil.createJoinQuery(
                  "customerNum", false, "num", Integer.class, fromQuery, s, mode);
          assertEquals("multipleValuesPerDocument=false, " + mode, List.of("c7"), ids(s, sv));
        }
      }
    }
  }

  private static Query withExists(Query q, String field) {
    return new BooleanQuery.Builder()
        .add(q, BooleanClause.Occur.MUST)
        .add(new FieldExistsQuery(field), BooleanClause.Occur.FILTER)
        .build();
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
