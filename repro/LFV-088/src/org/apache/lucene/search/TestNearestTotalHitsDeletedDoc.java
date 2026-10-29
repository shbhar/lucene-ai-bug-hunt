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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestNearestTotalHitsDeletedDoc extends LuceneTestCase {

  public void testDeletedDocNotCounted() throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 3; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
        doc.add(new LatLonPoint("point", 0, 0));
        w.addDocument(doc);
      }
      w.deleteDocuments(new Term("id", "0"));
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TotalHits total = LatLonPoint.nearest(searcher, "point", 0, 0, 10).totalHits;
        boolean exact = total.relation() == TotalHits.Relation.EQUAL_TO;
        // 2 live documents: the deleted one must not be counted; actual EQUAL_TO 3
        assertTrue(
            "expected exactly 2 hits (or a lower bound <= 2), got " + total,
            exact ? total.value() == 2 : total.value() <= 2);
      }
    }
  }
}
