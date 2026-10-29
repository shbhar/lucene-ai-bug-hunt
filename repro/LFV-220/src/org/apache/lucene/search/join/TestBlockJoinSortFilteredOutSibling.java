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

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestBlockJoinSortFilteredOutSibling extends LuceneTestCase {

  private static Document child(String type, String s) {
    Document d = new Document();
    d.add(new StringField("type", type, Field.Store.NO));
    d.add(new SortedDocValuesField("s", new BytesRef(s)));
    return d;
  }

  private static Document parent(String id) {
    Document d = new Document();
    d.add(new StringField("type", "parent", Field.Store.NO));
    d.add(new StoredField("id", id));
    return d;
  }

  public void testStringSortIgnoresSiblingOutsideChildFilter() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocuments(List.of(child("a", "y"), child("b", "x"), parent("P1")));
        w.addDocuments(List.of(child("a", "d"), parent("P2")));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        BitSetProducer parents =
            new QueryBitSetProducer(new TermQuery(new Term("type", "parent")));
        Query typeA = new TermQuery(new Term("type", "a"));
        SortField sort =
            new ToParentBlockJoinSortField(
                "s", SortField.Type.STRING, false, parents, new QueryBitSetProducer(typeA));
        ToParentBlockJoinQuery query = new ToParentBlockJoinQuery(typeA, parents, ScoreMode.None);
        StringBuilder actual = new StringBuilder();
        for (ScoreDoc hit : searcher.search(query, 10, new Sort(sort)).scoreDocs) {
          BytesRef s = (BytesRef) ((FieldDoc) hit).fields[0];
          actual
              .append(searcher.storedFields().document(hit.doc).get("id"))
              .append('=')
              .append(s == null ? null : s.utf8ToString())
              .append(' ');
        }
        // expected: P2 (min d) before P1 (min y); actual: P1 first, as if a type:a child lacked s
        assertEquals("P2=d P1=y", actual.toString().trim());
      }
    }
  }
}
