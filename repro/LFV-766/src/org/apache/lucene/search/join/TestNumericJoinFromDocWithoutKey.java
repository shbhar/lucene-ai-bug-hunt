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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestNumericJoinFromDocWithoutKey extends LuceneTestCase {

  public void testFromDocWithoutKeyDoesNotJoinKeyZero() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(
          List.of(
              new StringField("type", "order", Field.Store.NO),
              new NumericDocValuesField("customer", 7)));
      w.addDocument(List.of(new StringField("type", "order", Field.Store.NO))); // no customer key
      w.addDocument(List.of(new IntPoint("id", 7), new StoredField("id", 7)));
      w.addDocument(List.of(new IntPoint("id", 0), new StoredField("id", 0)));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        Query fromQuery = new TermQuery(new Term("type", "order"));
        Query join =
            JoinUtil.createJoinQuery(
                "customer", false, "id", Integer.class, fromQuery, s, ScoreMode.None);
        List<Integer> joined = new ArrayList<>();
        for (ScoreDoc hit : s.search(join, 10).scoreDocs) {
          joined.add(s.storedFields().document(hit.doc).getField("id").numericValue().intValue());
        }
        assertEquals("customers joined to the orders", List.of(7), joined);
      }
    }
  }
}
