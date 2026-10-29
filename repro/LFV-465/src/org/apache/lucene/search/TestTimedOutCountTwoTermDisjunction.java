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
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTimedOutCountTwoTermDisjunction extends LuceneTestCase {

  public void testTimedOutCountExceedsMaxDoc() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 22; i++) {
          Document doc = new Document();
          if (i < 21) doc.add(new StringField("f", "a", Field.Store.NO));
          if (i < 2) doc.add(new StringField("f", "b", Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        s.setQueryCache(null);
        s.setTimeout(() -> true);
        Query aOrB =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("f", "a")), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("f", "b")), BooleanClause.Occur.SHOULD)
                .build();
        // a in docs 0-20, b in docs 0-1: expected a timed-out count <= maxDoc 22, actual 23
        int count = s.count(aOrB);
        assertTrue(
            "expected a timed-out count() <= maxDoc 22 (21 docs match), got " + count,
            count <= r.maxDoc());
      }
    }
  }
}
