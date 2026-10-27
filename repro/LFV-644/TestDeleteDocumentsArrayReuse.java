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
 * {@link IndexWriter#deleteDocuments(Term...)} and {@link IndexWriter#deleteDocuments(Query...)}
 * should delete what the array held when the call was made, also if the caller reuses the array
 * after the call returned. Default {@link IndexWriterConfig}, public API only.
 */
public class TestDeleteDocumentsArrayReuse extends LuceneTestCase {

  /** A batch loop that refills one Term[2] for each batch of two ids. */
  public void testBatchLoopReusingTermArray() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 6; i++) {
        writer.addDocument(doc("d" + i));
      }
      writer.addDocument(doc("keep"));
      Term[] batch = new Term[2];
      for (int b = 0; b < 3; b++) {
        batch[0] = new Term("id", "d" + (2 * b));
        batch[1] = new Term("id", "d" + (2 * b + 1));
        writer.deleteDocuments(batch);
      }
      writer.commit();
      assertEquals(Set.of("keep"), liveIds(dir));
    }
  }

  /** The same two calls delete flushed documents correctly but not buffered ones. */
  public void testFlushedAndBufferedDocumentsDisagree() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
      writer.addDocument(doc("flushed1"));
      writer.addDocument(doc("flushed2"));
      writer.flush();
      writer.addDocument(doc("buffered1"));
      writer.addDocument(doc("buffered2"));
      writer.addDocument(doc("keep"));
      Term[] batch = {new Term("id", "flushed1"), new Term("id", "buffered1")};
      writer.deleteDocuments(batch);
      batch[0] = new Term("id", "flushed2");
      batch[1] = new Term("id", "buffered2");
      writer.deleteDocuments(batch);
      writer.commit();
      assertEquals(Set.of("keep"), liveIds(dir));
    }
  }

  /** Reassigning a slot after the call, with no further delete call. */
  public void testReassignedQuerySlot() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
      writer.addDocument(doc("delete-me"));
      writer.addDocument(doc("keep"));
      Query[] queries = {new TermQuery(new Term("id", "delete-me"))};
      writer.deleteDocuments(queries);
      queries[0] = new TermQuery(new Term("id", "keep"));
      writer.commit();
      assertEquals(Set.of("keep"), liveIds(dir));
    }
  }

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  private static Set<String> liveIds(Directory dir) throws IOException {
    Set<String> ids = new TreeSet<>();
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      for (LeafReaderContext ctx : reader.leaves()) {
        LeafReader leaf = ctx.reader();
        Bits liveDocs = leaf.getLiveDocs();
        StoredFields storedFields = leaf.storedFields();
        for (int doc = 0; doc < leaf.maxDoc(); doc++) {
          if (liveDocs == null || liveDocs.get(doc)) {
            ids.add(storedFields.document(doc).get("id"));
          }
        }
      }
    }
    return ids;
  }
}
