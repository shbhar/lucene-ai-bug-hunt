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
package org.apache.lucene.index;

import java.util.Comparator;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIndexWriterFromLeafSortedReader extends LuceneTestCase {

  public void testDeleteThroughWriterOpenedFromLeafSortedReaderCommit() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String id : new String[] {"a", "b"}) {
          Document doc = new Document();
          doc.add(new StringField("id", id, Field.Store.NO));
          w.addDocument(doc);
          w.commit();
        }
      }
      Comparator<LeafReader> reverseSegmentOrder =
          Comparator.comparing((LeafReader r) -> ((SegmentReader) r).getSegmentName()).reversed();
      try (DirectoryReader reader = DirectoryReader.open(dir, reverseSegmentOrder);
          IndexWriter w =
              new IndexWriter(dir, new IndexWriterConfig().setIndexCommit(reader.getIndexCommit()))) {
        w.deleteDocuments(new Term("id", "a"));
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        // expected: id:a deleted and id:b kept; actual: id:b is deleted and id:a survives
        assertEquals(0, searcher.count(new TermQuery(new Term("id", "a"))));
        assertEquals(1, searcher.count(new TermQuery(new Term("id", "b"))));
      }
    }
  }
}
