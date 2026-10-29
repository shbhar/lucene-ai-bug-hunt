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

import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;

public class TestDeleteDocumentsReusedQueryArray extends LuceneTestCase {

  public void testReusedQueryArray() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String id : new String[] {"delete-me", "keep"}) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.YES));
        writer.addDocument(doc);
      }
      Query[] queries = {new TermQuery(new Term("id", "delete-me"))};
      writer.deleteDocuments(queries);
      queries[0] = new TermQuery(new Term("id", "keep")); // the call has returned; reuse the array
      writer.commit();

      Set<String> live = new TreeSet<>();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LeafReader leaf = getOnlyLeafReader(reader);
        Bits liveDocs = leaf.getLiveDocs();
        for (int i = 0; i < leaf.maxDoc(); i++) {
          if (liveDocs == null || liveDocs.get(i)) {
            live.add(leaf.storedFields().document(i).get("id"));
          }
        }
      }
      // expected [keep] (the call deleted "delete-me"); actual [delete-me]: "keep" is deleted instead
      assertEquals(Set.of("keep"), live);
    }
  }
}
