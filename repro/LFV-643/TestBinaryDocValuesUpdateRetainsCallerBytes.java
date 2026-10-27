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
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;

/**
 * IndexWriter#updateBinaryDocValue and IndexWriter#updateDocValues(Term, Field...) keep a reference
 * to the caller's BytesRef. A document that is still buffered in RAM gets the bytes that BytesRef
 * holds when the writer next adds a document or flushes, not the bytes passed to the call. A
 * document in a flushed segment gets the bytes passed to the call.
 *
 * <p>Public API only; one thread; default codec; no merges; flushes only when a test asks for one.
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
        .setCodec(TestUtil.getDefaultCodec())
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
      assertEquals("flushed document in " + got, "b", got.get("flushed"));
      assertEquals("buffered document in " + got, "b", got.get("buffered"));
    }
  }

  /** One BytesRefBuilder reused for a loop of updates. */
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

  /**
   * No caller-side mutation at all: values are copied from another index by passing the BytesRef
   * returned by BinaryDocValues#binaryValue() straight to updateBinaryDocValue.
   */
  public void testPassThroughFromAnotherReader() throws IOException {
    try (Directory src = newDirectory();
        Directory dst = newDirectory()) {
      try (IndexWriter w = new IndexWriter(src, config())) {
        for (int i = 0; i < 3; i++) {
          w.addDocument(doc("doc" + i, "src" + i));
        }
      }
      try (IndexWriter writer = new IndexWriter(dst, config());
          DirectoryReader srcReader = DirectoryReader.open(src)) {
        for (int i = 0; i < 3; i++) {
          writer.addDocument(doc("doc" + i, "old"));
        }
        for (LeafReaderContext ctx : srcReader.leaves()) {
          BinaryDocValues bdv = ctx.reader().getBinaryDocValues("bdv");
          StoredFields storedFields = ctx.reader().storedFields();
          for (int doc = 0; doc < ctx.reader().maxDoc(); doc++) {
            assertTrue(bdv.advanceExact(doc));
            Term id = new Term("id", storedFields.document(doc).get("id"));
            writer.updateBinaryDocValue(id, "bdv", bdv.binaryValue());
          }
        }
        writer.commit();
      }
      Map<String, String> got = values(dst);
      for (int i = 0; i < 3; i++) {
        assertEquals("doc" + i + " in " + got, "src" + i, got.get("doc" + i));
      }
    }
  }

  /**
   * Secondary case, same root cause: updateDocValues(Term, Field...) with one BinaryDocValuesField
   * over a reused BytesRefBuilder (Field's javadoc says its BytesRef is not copied).
   */
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

  /** Control: a fresh BytesRef per call gives every buffered document its own value. */
  public void testControlFreshBytesRefPerCall() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, config())) {
      for (int i = 0; i < 3; i++) {
        writer.addDocument(doc("doc" + i, "old"));
      }
      for (int i = 0; i < 3; i++) {
        writer.updateBinaryDocValue(new Term("id", "doc" + i), "bdv", new BytesRef("new" + i));
      }
      writer.commit();
      Map<String, String> got = values(dir);
      for (int i = 0; i < 3; i++) {
        assertEquals("doc" + i + " in " + got, "new" + i, got.get("doc" + i));
      }
    }
  }
}
