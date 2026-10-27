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

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndexWriter#updateDocuments and #softUpdateDocuments accept an empty block (delete or soft-delete
 * by term, add nothing). When no document is buffered, the call takes a fresh
 * DocumentsWriterPerThread, which accounts its initial buffers in the flush control's active bytes
 * but holds no document. The next full flush (commit, flush, NRT reopen) drops such a 0-document
 * writer "on the floor" without releasing its bytes, so IndexWriter#ramBytesUsed() grows by about
 * 33 KB per call for the life of the writer. Once it exceeds the RAM buffer, every following
 * document triggers a flush (one segment per document). With assertions enabled, close() fails in
 * DocumentsWriter.abort ("netBytes has unexpected value").
 */
public class TestEmptyBlockUpdateLeaksFlushRam extends LuceneTestCase {

  private static final String SOFT = "soft";

  private static IndexWriterConfig config() {
    return new IndexWriterConfig()
        .setSoftDeletesField(SOFT)
        .setRAMBufferSizeMB(1.0)
        .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
        .setMergePolicy(NoMergePolicy.INSTANCE);
  }

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  private interface Op {
    void run(IndexWriter w) throws Exception;
  }

  private static void check(Op emptyBlockOp) throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, config());
      try {
        w.addDocument(doc("a"));
        w.commit();
        for (int i = 0; i < 100; i++) {
          emptyBlockOp.run(w);
          w.commit();
        }
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals(0, new IndexSearcher(r).count(new TermQuery(new Term("id", "a")))); // the delete worked
        }
        // nothing is buffered: the writer must not account megabytes of RAM
        assertTrue(
            "ramBytesUsed=" + w.ramBytesUsed() + " with no buffered document (RAM buffer 1 MB)",
            w.ramBytesUsed() < 1024 * 1024);
        for (int i = 0; i < 200; i++) {
          w.addDocument(doc("n" + i));
        }
        w.commit();
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertTrue(
              "200 tiny documents were flushed into " + r.leaves().size() + " segments",
              r.leaves().size() <= 3);
        }
      } catch (Throwable t) {
        try {
          w.close();
        } catch (Throwable onClose) {
          t.addSuppressed(onClose); // with -ea close() fails too (see testCloseAfterOneEmptyBlockUpdate)
        }
        throw t;
      }
      w.close();
    }
  }

  public void testUpdateDocumentsWithEmptyBlock() throws Exception {
    check(w -> w.updateDocuments(new Term("id", "a"), List.of()));
  }

  public void testSoftUpdateDocumentsWithEmptyBlock() throws Exception {
    check(
        w ->
            w.softUpdateDocuments(
                new Term("id", "a"), List.of(), new NumericDocValuesField(SOFT, 1)));
  }

  /** A single empty-block update after a commit is enough for close() to fail under -ea. */
  public void testCloseAfterOneEmptyBlockUpdate() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, config());
      w.addDocument(doc("a"));
      w.commit();
      w.updateDocuments(new Term("id", "a"), List.of());
      w.close(); // AssertionError: netBytes has unexpected value 0 != ... (with -ea)
    }
  }

  /** Control: addDocuments with an empty block takes no writer and leaks nothing. */
  public void testControlAddDocumentsWithEmptyBlock() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, config());
      try {
        w.addDocument(doc("a"));
        w.commit();
        for (int i = 0; i < 100; i++) {
          w.addDocuments(List.of());
          w.commit();
        }
        assertTrue(w.ramBytesUsed() < 1024 * 1024);
      } finally {
        w.close();
      }
    }
  }
}
