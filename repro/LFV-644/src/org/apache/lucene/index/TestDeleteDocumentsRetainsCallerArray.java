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

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;

/**
 * IndexWriter#deleteDocuments(Term...) and #deleteDocuments(Query...) keep the caller's array. For
 * documents still buffered in RAM the deletes are resolved from that array when the per-thread
 * writer next applies its delete slice (next addDocument or flush), so a caller that reuses its
 * array after the call returned deletes the wrong documents.
 *
 * <p>Only public API is used. Fixed IndexWriterConfig (no random flushes or merges).
 */
public class TestDeleteDocumentsRetainsCallerArray extends LuceneTestCase {

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  private static IndexWriterConfig config() {
    return new IndexWriterConfig()
        .setMergePolicy(NoMergePolicy.INSTANCE)
        .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
        .setRAMBufferSizeMB(64);
  }

  private static Set<String> liveIds(Directory dir) throws IOException {
    Set<String> out = new TreeSet<>();
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      for (LeafReaderContext ctx : reader.leaves()) {
        LeafReader leaf = ctx.reader();
        Bits liveDocs = leaf.getLiveDocs();
        StoredFields storedFields = leaf.storedFields();
        for (int doc = 0; doc < leaf.maxDoc(); doc++) {
          if (liveDocs == null || liveDocs.get(doc)) {
            out.add(storedFields.document(doc).get("id"));
          }
        }
      }
    }
    return out;
  }

  /** A reused batch array of terms: the first batch is lost for buffered documents. */
  public void testReusedTermArray() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, config())) {
      writer.addDocument(doc("flushed1"));
      writer.addDocument(doc("flushed2"));
      writer.flush();
      writer.addDocument(doc("buffered1"));
      writer.addDocument(doc("buffered2"));
      writer.addDocument(doc("keep"));

      Term[] batch = new Term[2];
      batch[0] = new Term("id", "flushed1");
      batch[1] = new Term("id", "buffered1");
      writer.deleteDocuments(batch);
      batch[0] = new Term("id", "flushed2"); // refill the same array for the next batch
      batch[1] = new Term("id", "buffered2");
      writer.deleteDocuments(batch);

      writer.commit();
      assertEquals(Set.of("keep"), liveIds(dir));
    }
  }

  /** A reused query array: a document that was never passed to deleteDocuments is deleted. */
  public void testReusedQueryArray() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, config())) {
      writer.addDocument(doc("delete-me"));
      writer.addDocument(doc("keep"));

      Query[] queries = {new TermQuery(new Term("id", "delete-me"))};
      writer.deleteDocuments(queries);
      queries[0] = new TermQuery(new Term("id", "keep")); // caller reuses its array, no delete call

      writer.commit();
      assertEquals(Set.of("keep"), liveIds(dir));
    }
  }
}
