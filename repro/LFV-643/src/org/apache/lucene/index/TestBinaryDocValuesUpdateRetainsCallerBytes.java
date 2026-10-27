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
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;

/**
 * IndexWriter#updateBinaryDocValue and IndexWriter#updateDocValues(Term, Field...) keep a reference
 * to the caller's BytesRef. Documents that are still buffered in RAM receive the bytes the BytesRef
 * holds when the per-thread writer next applies its delete slice (next addDocument or flush), not
 * the bytes it held when the update call returned.
 *
 * <p>Only public API is used. Fixed IndexWriterConfig (no random flushes or merges).
 */
public class TestBinaryDocValuesUpdateRetainsCallerBytes extends LuceneTestCase {

  private static Document doc(String id, String value) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new BinaryDocValuesField("bdv", new BytesRef(value)));
    return d;
  }

  private static IndexWriterConfig config() {
    return new IndexWriterConfig()
        .setMergePolicy(NoMergePolicy.INSTANCE)
        .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
        .setRAMBufferSizeMB(64);
  }

  /** id -> binary doc value of every document in the committed index. */
  private static Map<String, String> values(Directory dir) throws IOException {
    Map<String, String> out = new HashMap<>();
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      for (LeafReaderContext ctx : reader.leaves()) {
        LeafReader leaf = ctx.reader();
        BinaryDocValues bdv = leaf.getBinaryDocValues("bdv");
        StoredFields storedFields = leaf.storedFields();
        for (int doc = 0; doc < leaf.maxDoc(); doc++) {
          assertTrue(bdv.advanceExact(doc));
          out.put(storedFields.document(doc).get("id"), bdv.binaryValue().utf8ToString());
        }
      }
    }
    return out;
  }

  /**
   * The caller changes its own BytesRef after both update calls have returned. The flushed document
   * gets the value it was passed; the buffered document gets the later bytes.
   */
  public void testBytesChangedAfterUpdateReturned() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, config())) {
      writer.addDocument(doc("flushed", "a"));
      writer.flush();
      writer.addDocument(doc("buffered", "a"));

      BytesRef value = new BytesRef("b");
      writer.updateBinaryDocValue(new Term("id", "flushed"), "bdv", value);
      writer.updateBinaryDocValue(new Term("id", "buffered"), "bdv", value);
      value.bytes[value.offset] = 'c'; // after both calls returned

      writer.commit();
      Map<String, String> got = values(dir);
      assertEquals("flushed segment", "b", got.get("flushed"));
      assertEquals("buffered document", "b", got.get("buffered"));
    }
  }

  /** The usual reuse idiom: one BytesRefBuilder for a loop of updates. */
  public void testReusedBytesRefBuilder() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, config())) {
      for (int i = 0; i < 3; i++) {
        writer.addDocument(doc("doc" + i, "old"));
      }
      BytesRefBuilder scratch = new BytesRefBuilder();
      for (int i = 0; i < 3; i++) {
        scratch.copyChars("new" + i);
        writer.updateBinaryDocValue(new Term("id", "doc" + i), "bdv", scratch.get());
      }
      writer.commit();
      Map<String, String> got = values(dir);
      for (int i = 0; i < 3; i++) {
        assertEquals("doc" + i + " in " + got, "new" + i, got.get("doc" + i));
      }
    }
  }

  /** Same root cause through updateDocValues(Term, Field...) with a reused BinaryDocValuesField. */
  public void testUpdateDocValuesWithReusedField() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, config())) {
      writer.addDocument(doc("doc1", "old"));
      writer.addDocument(doc("doc2", "old"));
      BytesRefBuilder scratch = new BytesRefBuilder();
      BinaryDocValuesField field = new BinaryDocValuesField("bdv", scratch.get());
      scratch.copyChars("x1");
      writer.updateDocValues(new Term("id", "doc1"), field);
      scratch.copyChars("x2");
      writer.updateDocValues(new Term("id", "doc2"), field);
      writer.commit();
      Map<String, String> got = values(dir);
      assertEquals("doc1 in " + got, "x1", got.get("doc1"));
      assertEquals("doc2 in " + got, "x2", got.get("doc2"));
    }
  }
}
