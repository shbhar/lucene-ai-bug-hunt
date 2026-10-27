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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.tests.store.BaseDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * IndexWriterConfig#setIndexCommit documents that an IndexWriter can be opened "from a
 * near-real-time reader, if you pass the reader's DirectoryReader#getIndexCommit". With a
 * soft-deletes field configured and a soft-deleted document in a segment, the new writer counts
 * that soft delete three times (the commit's soft-delete count, plus the reader's live docs once as
 * pending soft deletes and once as pending hard deletes). Its first commit then drops the segment as
 * fully deleted, writes a segments file that cannot be read, or turns the soft delete into a hard
 * delete, depending on the segment size. With assertions enabled the writer constructor fails on
 * "softDeleteCount doesn't match".
 */
public class TestWriterFromSoftDeletesNrtReader extends LuceneTestCase {

  private static IndexWriterConfig config() {
    return new IndexWriterConfig()
        .setSoftDeletesField("soft")
        .setMergePolicy(NoMergePolicy.INSTANCE);
  }

  /** IndexWriterConfig#setSoftDeletesField: documents are indexed via softUpdateDocument. */
  private static void index(IndexWriter w, String id) throws IOException {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    w.softUpdateDocument(new Term("id", id), d, new NumericDocValuesField("soft", 1));
  }

  /** What the writer opened from the NRT reader reported, and the directory it committed to. */
  private record Result(BaseDirectoryWrapper dir, IndexWriter.DocStats stats) {}

  /**
   * One segment of {@code maxDoc} documents, the first one soft-deleted and committed; an NRT reader
   * is opened, the writer closed (nothing new to commit), and a new writer is opened from the
   * reader's IndexCommit (the flow of TestIndexWriterFromReader#testRightAfterCommit) and closed.
   */
  private static Result writeThroughWriterFromNrtReader(int maxDoc) throws IOException {
    BaseDirectoryWrapper dir = newDirectory();
    dir.setCheckIndexOnClose(false); // the test checks the index itself
    IndexWriter w = new IndexWriter(dir, config());
    for (int i = 0; i < maxDoc; i++) {
      index(w, "id" + i);
    }
    w.commit();
    // soft delete, as documented: "Deletes are applied via IndexWriter#updateDocValues"
    w.updateDocValues(new Term("id", "id0"), new NumericDocValuesField("soft", 1));
    w.commit();
    DirectoryReader nrt = DirectoryReader.open(w);
    assertEquals(maxDoc, nrt.maxDoc());
    assertEquals(maxDoc - 1, nrt.numDocs());
    w.close();
    IndexWriter.DocStats stats;
    try {
      IndexWriter w2 = new IndexWriter(dir, config().setIndexCommit(nrt.getIndexCommit()));
      stats = w2.getDocStats();
      w2.close();
    } catch (Throwable t) {
      IOUtils.closeWhileSuppressingExceptions(t, nrt, dir);
      throw t;
    }
    nrt.close();
    return new Result(dir, stats);
  }

  private static void check(int maxDoc, Result res) throws IOException {
    try (BaseDirectoryWrapper dir = res.dir();
        DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals("maxDoc after the commit", maxDoc, r.maxDoc());
      assertEquals("documents without a hard delete after the commit", maxDoc, r.numDocs());
      assertEquals(maxDoc - 1, new SoftDeletesDirectoryReaderWrapper(r, "soft").numDocs());
    }
    assertEquals("writer opened from the NRT reader: maxDoc", maxDoc, res.stats().maxDoc);
    assertEquals("writer opened from the NRT reader: numDocs", maxDoc - 1, res.stats().numDocs);
  }

  /** 3 documents, 1 soft-deleted: the next commit drops the whole segment (2 live documents). */
  public void testSegmentIsNotDropped() throws IOException {
    check(3, writeThroughWriterFromNrtReader(3));
  }

  /** 2 documents, 1 soft-deleted: the commit records 3 deletions for 2 documents. */
  public void testCommitCanBeOpened() throws IOException {
    check(2, writeThroughWriterFromNrtReader(2));
  }

  /** 10 documents, 1 soft-deleted: the soft-deleted document must not become hard-deleted. */
  public void testSoftDeleteStaysSoft() throws IOException {
    check(10, writeThroughWriterFromNrtReader(10));
  }
  /**
   * No soft delete at all, only a soft-deletes field configured: an uncommitted hard delete seen by
   * the NRT reader is counted twice, so after the rollback-to-NRT-point flow of
   * TestIndexWriterFromReader#testAfterRollback the 2-document segment is dropped with its live
   * document.
   */
  public void testUncommittedHardDeleteDoesNotDropSegment() throws IOException {
    try (BaseDirectoryWrapper dir = newDirectory()) {
      dir.setCheckIndexOnClose(false);
      IndexWriter w = new IndexWriter(dir, config());
      for (int i = 0; i < 2; i++) {
        index(w, "id" + i);
      }
      w.commit();
      w.deleteDocuments(new Term("id", "id0"));
      DirectoryReader nrt = DirectoryReader.open(w);
      assertEquals(1, nrt.numDocs());
      w.rollback();
      IndexWriter.DocStats stats;
      try {
        IndexWriter w2 = new IndexWriter(dir, config().setIndexCommit(nrt.getIndexCommit()));
        stats = w2.getDocStats();
        w2.close();
      } catch (Throwable t) {
        IOUtils.closeWhileSuppressingExceptions(t, nrt);
        throw t;
      }
      nrt.close();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals("maxDoc after the commit", 2, r.maxDoc());
        assertEquals("live documents after the commit", 1, r.numDocs());
      }
      assertEquals("writer opened from the NRT reader: numDocs", 1, stats.numDocs);
    }
  }
}
