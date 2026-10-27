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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;

/**
 * Removing the soft-deletes value (a doc-values update with a null value) of a document that is
 * hard-deleted must not make that document live again.
 *
 * <p>Contract: {@link IndexWriterConfig#setSoftDeletesField(String)} says "If soft-deletes are
 * enabled, documents can still be hard-deleted. Hard-deleted documents won't be considered as
 * soft-deleted even if they have a value in the soft-deletes field." {@link
 * IndexWriter#tryUpdateDocValue} says a null value "can be used to un-delete a soft-deleted
 * document". Nothing allows un-deleting a hard-deleted document.
 *
 * <p>Public API only: an in-memory directory, explicit configs and no random() calls (the test
 * framework still randomizes the default codec and other defaults per seed). Docs a, b, c are doc
 * 0, 1, 2 of one segment.
 */
public class TestSoftValueRemovalOfHardDeletedDoc extends LuceneTestCase {

  private static final String SOFT = "soft_delete";

  private static IndexWriterConfig config() {
    return new IndexWriterConfig().setSoftDeletesField(SOFT).setMergePolicy(NoMergePolicy.INSTANCE);
  }

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  private static NumericDocValuesField softDelete() {
    return new NumericDocValuesField(SOFT, 1);
  }

  private static NumericDocValuesField removeSoftValue() {
    return new NumericDocValuesField(SOFT, (Long) null);
  }

  private static void addAbcAndCommit(IndexWriter w) throws IOException {
    w.addDocument(doc("a"));
    w.addDocument(doc("b"));
    w.addDocument(doc("c"));
    w.commit();
  }

  /** Ids of the live documents, read from the live docs of every leaf. */
  private static List<String> liveIds(IndexReader r) throws IOException {
    List<String> ids = new ArrayList<>();
    for (LeafReaderContext ctx : r.leaves()) {
      Bits live = ctx.reader().getLiveDocs();
      StoredFields stored = ctx.reader().storedFields();
      for (int d = 0; d < ctx.reader().maxDoc(); d++) {
        if (live == null || live.get(d)) {
          ids.add(stored.document(d).get("id"));
        }
      }
    }
    return ids;
  }

  private static long countId(IndexReader r, String id) throws IOException {
    return new IndexSearcher(r).count(new TermQuery(new Term("id", id)));
  }

  /** Control: the documented use, un-deleting a soft-deleted document, works. */
  public void testControlUndeleteSoftDeletedDoc() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      addAbcAndCommit(w);
      w.updateDocValues(new Term("id", "b"), softDelete());
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(List.of("a", "c"), liveIds(r));
        assertTrue(w.tryUpdateDocValue(r, 1, removeSoftValue()) > 0);
      }
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(List.of("a", "b", "c"), liveIds(r));
      }
    }
  }

  /**
   * The core case. b is soft-deleted, a is hard-deleted, then a's (absent) soft value is removed. a
   * must stay deleted. On affected versions the NRT reader shows a again, with no error.
   */
  public void testHardDeletedDocStaysDeleted() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      addAbcAndCommit(w);
      w.updateDocValues(new Term("id", "b"), softDelete());
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals("a", r.storedFields().document(0).get("id"));
        w.deleteDocuments(new Term("id", "a"));
        assertTrue(w.tryUpdateDocValue(r, 0, removeSoftValue()) > 0);
      }
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals("live docs", List.of("c"), liveIds(r));
        assertEquals("hits for the hard-deleted doc a", 0, countId(r, "a"));
      }
    }
  }

  /**
   * Same sequence, then a merge in the same writer session. The merged, committed segment must not
   * contain the hard-deleted doc a. (On affected versions it does, and the commit still passes
   * CheckIndex, so the failure is the live-docs assertion below.)
   */
  public void testMergeDoesNotKeepHardDeletedDoc() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      // with only two segments LogDocMergePolicy finds no natural merge, so only forceMerge(1)
      // merges; SerialMergeScheduler runs it in this thread
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setSoftDeletesField(SOFT)
              .setMergePolicy(new LogDocMergePolicy())
              .setMergeScheduler(new SerialMergeScheduler());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        addAbcAndCommit(w);
        w.addDocument(doc("x"));
        w.commit(); // a second segment, so forceMerge(1) has work to do
        w.updateDocValues(new Term("id", "b"), softDelete());
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals("a", r.storedFields().document(0).get("id"));
          w.deleteDocuments(new Term("id", "a"));
          assertTrue(w.tryUpdateDocValue(r, 0, removeSoftValue()) > 0);
        }
        w.forceMerge(1);
        w.commit();
      }
      TestUtil.checkIndex(dir); // passes either way: only the live docs show a lost delete
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals("committed live docs after the merge", List.of("c", "x"), liveIds(r));
        assertEquals("hits for the hard-deleted doc a", 0, countId(r, "a"));
      }
    }
  }

  /** Same sequence, then commit: the commit must pass CheckIndex. */
  public void testCommitPassesCheckIndex() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        addAbcAndCommit(w);
        w.updateDocValues(new Term("id", "b"), softDelete());
        try (DirectoryReader r = DirectoryReader.open(w)) {
          w.deleteDocuments(new Term("id", "a"));
          assertTrue(w.tryUpdateDocValue(r, 0, removeSoftValue()) > 0);
        }
        w.commit();
      }
      TestUtil.checkIndex(dir);
    }
  }

  /**
   * No other soft-deleted doc: a is hard-deleted, then its soft value is removed. The next NRT
   * reader must open and show b, c. On affected versions the soft-delete count goes to -1, and
   * opening a reader fails (and so does every commit and close with assertions disabled).
   */
  public void testWriterStaysUsable() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, config());
      try {
        addAbcAndCommit(w);
        try (DirectoryReader r = DirectoryReader.open(w)) {
          w.deleteDocuments(new Term("id", "a"));
          assertTrue(w.tryUpdateDocValue(r, 0, removeSoftValue()) > 0);
        }
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals(List.of("b", "c"), liveIds(r));
        }
      } finally {
        w.rollback();
      }
    }
  }

  /** The documented un-delete of b, then an ordinary hard delete of b. b must end up deleted. */
  public void testUndeleteThenHardDelete() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, config());
      try {
        addAbcAndCommit(w);
        w.updateDocValues(new Term("id", "b"), softDelete());
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertTrue(w.tryUpdateDocValue(r, 1, removeSoftValue()) > 0); // documented un-delete
          w.deleteDocuments(new Term("id", "b"));
        }
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals(List.of("a", "c"), liveIds(r));
        }
      } finally {
        w.rollback();
      }
    }
  }

  /**
   * The documented un-delete of b, then an ordinary hard delete of b, in a segment that also holds
   * another soft-deleted doc (d). b must end up deleted. On affected versions b is live again, with
   * no error, because d's soft delete keeps the soft-delete count from going below zero.
   */
  public void testUndeleteThenHardDeleteWithAnotherSoftDeletedDoc() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(doc("a"));
      w.addDocument(doc("b"));
      w.addDocument(doc("c"));
      w.addDocument(doc("d"));
      w.commit();
      w.updateDocValues(new Term("id", "b"), softDelete());
      w.updateDocValues(new Term("id", "d"), softDelete());
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(List.of("a", "c"), liveIds(r));
        assertTrue(w.tryUpdateDocValue(r, 1, removeSoftValue()) > 0); // documented un-delete of b
        w.deleteDocuments(new Term("id", "b"));
      }
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals("live docs", List.of("a", "c"), liveIds(r));
        assertEquals("hits for the hard-deleted doc b", 0, countId(r, "b"));
      }
    }
  }

  /**
   * Term-based API only (no tryUpdateDocValue): the null update is resolved when the in-memory
   * segment is flushed (maxBufferedDocs=3), before the hard delete of the same doc is applied.
   */
  public void testTermApiOnly() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, config().setMaxBufferedDocs(3));
      try {
        w.addDocument(doc("a"));
        w.addDocument(doc("b"));
        w.updateDocValues(new Term("id", "a"), removeSoftValue()); // a has no soft value to remove
        w.addDocument(doc("c")); // third buffered doc: flush
        w.deleteDocuments(new Term("id", "a"));
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals(List.of("b", "c"), liveIds(r));
        }
      } finally {
        w.rollback();
      }
    }
  }
}
