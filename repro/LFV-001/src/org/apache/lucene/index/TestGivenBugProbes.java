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
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.ControlledRealTimeReopenThread;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;

/**
 * Probes for the "openIfChanged misses doc-values updates written without a SIS version bump" bug.
 *
 * <p>Every test asserts the CORRECT (expected) behaviour, using a fresh NRT reader as the oracle:
 * whatever DirectoryReader.open(writer) shows must also be shown by openIfChanged(old, writer) (or
 * by the old reader, if openIfChanged returns null). A failing test therefore = a manifestation of
 * the bug. Tests whose names start with "control" are expected to pass on unpatched main.
 */
public class TestGivenBugProbes extends LuceneTestCase {

  static final String SOFT = "soft_delete";

  static IndexWriterConfig iwc(boolean softDeletes, boolean noMerges) {
    IndexWriterConfig iwc = new IndexWriterConfig().setMergeScheduler(new SerialMergeScheduler());
    if (noMerges) {
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    }
    if (softDeletes) {
      iwc.setSoftDeletesField(SOFT);
    }
    return iwc;
  }

  static Document doc(String id, long val) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    doc.add(new NumericDocValuesField("val", val));
    doc.add(new BinaryDocValuesField("bin", new BytesRef("b" + val)));
    return doc;
  }

  static IndexWriter writer(Directory dir, boolean softDeletes, boolean noMerges)
      throws IOException {
    IndexWriter w = new IndexWriter(dir, iwc(softDeletes, noMerges));
    w.addDocument(doc("1", 1));
    w.addDocument(doc("2", 1));
    return w;
  }

  /** Logical view of a reader: id -> "val/bin" for every live doc. */
  static Map<String, String> view(IndexReader r) throws IOException {
    Map<String, String> m = new TreeMap<>();
    for (LeafReaderContext ctx : r.leaves()) {
      LeafReader leaf = ctx.reader();
      Bits live = leaf.getLiveDocs();
      NumericDocValues val = leaf.getNumericDocValues("val");
      BinaryDocValues bin = leaf.getBinaryDocValues("bin");
      StoredFields sf = leaf.storedFields();
      for (int d = 0; d < leaf.maxDoc(); d++) {
        if (live != null && live.get(d) == false) continue;
        String id = sf.document(d).get("id");
        String v = (val != null && val.advanceExact(d)) ? Long.toString(val.longValue()) : "-";
        String b = (bin != null && bin.advanceExact(d)) ? bin.binaryValue().utf8ToString() : "-";
        m.put(id, v + "/" + b);
      }
    }
    return m;
  }

  /**
   * The oracle: reopen r from w; the result (or r itself if null) must equal a fresh NRT reader.
   * Returns the reader to keep using (caller closes).
   */
  static DirectoryReader reopenAndCheck(
      String what, DirectoryReader r, IndexWriter w, boolean applyAllDeletes, boolean writeAll)
      throws IOException {
    DirectoryReader r2 = DirectoryReader.openIfChanged(r, w, applyAllDeletes);
    DirectoryReader current = r2 == null ? r : r2;
    Map<String, String> fresh;
    try (DirectoryReader f = DirectoryReader.open(w, applyAllDeletes, writeAll)) {
      fresh = view(f);
    }
    Map<String, String> seen = view(current);
    if (r2 != null) {
      r.close();
    }
    if (fresh.equals(seen) == false) {
      current.close();
      throw new AssertionError(
          what
              + ": openIfChanged returned "
              + (r2 == null ? "null" : "a new reader")
              + " but reopened view "
              + seen
              + " != fresh view "
              + fresh);
    }
    return current;
  }

  static DirectoryReader reopenAndCheck(String what, DirectoryReader r, IndexWriter w)
      throws IOException {
    return reopenAndCheck(what, r, w, true, false);
  }

  // ---------------------------------------------------------------- manifestations (expected FAIL)

  public void testNumericUpdateThenFlush() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false, true)) {
      DirectoryReader r = DirectoryReader.open(w);
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      w.flush();
      reopenAndCheck("numeric update + flush", r, w).close();
    }
  }

  public void testBinaryUpdateThenFlush() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false, true)) {
      DirectoryReader r = DirectoryReader.open(w);
      w.updateBinaryDocValue(new Term("id", "1"), "bin", new BytesRef("b2"));
      w.flush();
      reopenAndCheck("binary update + flush", r, w).close();
    }
  }

  public void testSoftDeleteThenFlush() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, true, true)) {
      DirectoryReader r = DirectoryReader.open(w);
      w.updateDocValues(new Term("id", "1"), new NumericDocValuesField(SOFT, 1));
      w.flush();
      reopenAndCheck("soft delete + flush", r, w).close();
    }
  }

  /** isCurrent() must be false when a fresh reader would differ. */
  public void testIsCurrentAfterUpdateThenFlush() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false, true)) {
      DirectoryReader r = DirectoryReader.open(w);
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      assertFalse("before flush: pending update -> not current", r.isCurrent());
      w.flush();
      boolean cur = r.isCurrent();
      r.close();
      assertFalse("after flush: r.isCurrent() must be false (fresh reader differs)", cur);
    }
  }

  /** NRT reader opened with writeAllDeletes=true (as Lucene's replicator PrimaryNode does). */
  public void testWriteAllDeletesReaderNumericUpdateNoFlush() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false, true)) {
      DirectoryReader r = DirectoryReader.open(w, true, true);
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      reopenAndCheck("writeAllDeletes reader, numeric update, no flush", r, w, true, true).close();
    }
  }

  public void testWriteAllDeletesReaderSoftDeleteNoFlush() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, true, true)) {
      DirectoryReader r = DirectoryReader.open(w, true, true);
      w.updateDocValues(new Term("id", "1"), new NumericDocValuesField(SOFT, 1));
      reopenAndCheck("writeAllDeletes reader, soft delete, no flush", r, w, true, true).close();
    }
  }

  /** Reader opened with applyAllDeletes=false, reopened with applyAllDeletes=false. */
  public void testApplyAllDeletesFalseReader() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false, true)) {
      DirectoryReader r = DirectoryReader.open(w, false, false);
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      w.flush();
      reopenAndCheck("applyAllDeletes=false reader, update + flush", r, w, false, false).close();
    }
  }

  /** forceMerge(1) on an index that is already one segment only flushes. */
  public void testForceMergeOneSegmentAfterUpdate() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false, false)) {
      w.forceMerge(1);
      DirectoryReader r = DirectoryReader.open(w);
      assertEquals(1, r.leaves().size());
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      w.forceMerge(1);
      reopenAndCheck("numeric update + forceMerge(1) of single segment", r, w).close();
    }
  }

  public void testForceMergeDeletesAfterNumericUpdate() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false, false)) {
      DirectoryReader r = DirectoryReader.open(w);
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      w.forceMergeDeletes();
      reopenAndCheck("numeric update + forceMergeDeletes", r, w).close();
    }
  }

  /** A no-op hard delete (matches nothing) does not end the staleness. */
  public void testUpdateFlushThenNoMatchDelete() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false, true)) {
      DirectoryReader r = DirectoryReader.open(w);
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      w.flush();
      w.deleteDocuments(new Term("id", "does-not-exist"));
      reopenAndCheck("update + flush + no-op delete", r, w).close();
    }
  }

  /**
   * Staleness is not transient: repeated reopen attempts (each followed by another flush) keep
   * returning a reader that differs from a fresh one.
   */
  public void testStalenessPersistsAcrossRepeatedReopens() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false, true)) {
      DirectoryReader r = DirectoryReader.open(w);
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      w.flush();
      int stale = 0;
      for (int i = 0; i < 5; i++) {
        DirectoryReader r2 = DirectoryReader.openIfChanged(r, w);
        if (r2 != null) {
          r.close();
          r = r2;
        }
        try (DirectoryReader fresh = DirectoryReader.open(w)) {
          if (view(fresh).equals(view(r)) == false) {
            stale++;
          }
        }
        w.flush();
      }
      r.close();
      assertEquals("reopen attempts that still did not see the update", 0, stale);
    }
  }

  /** SearcherManager.maybeRefresh after an explicit flush. */
  public void testSearcherManagerMaybeRefreshAfterFlush() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false, true)) {
      SearcherManager mgr = new SearcherManager(w, null);
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      w.flush();
      mgr.maybeRefreshBlocking();
      IndexSearcher s = mgr.acquire();
      Map<String, String> seen;
      try {
        seen = view(s.getIndexReader());
      } finally {
        mgr.release(s);
      }
      mgr.close();
      assertEquals("SearcherManager must see the update after maybeRefreshBlocking", "2/b1", seen.get("1"));
    }
  }

  /** SearcherManager(writer, true, true, ...) = the replicator PrimaryNode configuration. */
  public void testSearcherManagerWriteAllDeletesNoFlush() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, true, true)) {
      SearcherManager mgr = new SearcherManager(w, true, true, null);
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      w.updateDocValues(new Term("id", "2"), new NumericDocValuesField(SOFT, 1));
      boolean refreshed = mgr.maybeRefresh();
      IndexSearcher s = mgr.acquire();
      Map<String, String> seen;
      try {
        seen = view(s.getIndexReader());
      } finally {
        mgr.release(s);
      }
      mgr.close();
      assertEquals(
          "SearcherManager(w,true,true) must see DV update + soft delete (maybeRefresh returned "
              + refreshed
              + ")",
          Map.of("1", "2/b1"),
          seen);
    }
  }

  /** ControlledRealTimeReopenThread.waitForGeneration returns though the searcher is stale. */
  public void testControlledRealTimeReopenThreadGeneration() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false, true)) {
      SearcherManager mgr = new SearcherManager(w, null);
      ControlledRealTimeReopenThread<IndexSearcher> crt =
          new ControlledRealTimeReopenThread<>(
              w, mgr, Duration.ofMillis(200), Duration.ofMillis(10));
      crt.setDaemon(true);
      crt.start();
      try {
        long gen = w.updateNumericDocValue(new Term("id", "1"), "val", 2);
        w.flush(); // e.g. a background "write indexing buffer" / or the app's own flush
        assertTrue(crt.waitForGeneration(gen, 10_000));
        IndexSearcher s = mgr.acquire();
        Map<String, String> seen;
        try {
          seen = view(s.getIndexReader());
        } finally {
          mgr.release(s);
        }
        assertEquals(
            "waitForGeneration(" + gen + ") returned, so the update must be visible",
            "2/b1",
            seen.get("1"));
      } finally {
        crt.close();
        mgr.close();
      }
    }
  }

  // --------------------------------------------------------------------- controls (expected PASS)

  public void testControlNumericUpdateNoFlush() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false, true)) {
      DirectoryReader r = DirectoryReader.open(w);
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      reopenAndCheck("update, no flush", r, w).close();
    }
  }

  public void testControlHardDeleteThenFlush() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false, true)) {
      DirectoryReader r = DirectoryReader.open(w);
      w.deleteDocuments(new Term("id", "1"));
      w.flush();
      reopenAndCheck("hard delete + flush", r, w).close();
    }
  }

  public void testControlSoftDeleteThenCommit() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, true, true)) {
      DirectoryReader r = DirectoryReader.open(w);
      w.updateDocValues(new Term("id", "1"), new NumericDocValuesField(SOFT, 1));
      w.commit();
      reopenAndCheck("soft delete + commit", r, w).close();
    }
  }

  /** softUpdateDocument adds a document too (Elasticsearch/OpenSearch style), so a new segment. */
  public void testControlSoftUpdateDocumentThenFlush() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, true, true)) {
      DirectoryReader r = DirectoryReader.open(w);
      w.softUpdateDocument(new Term("id", "1"), doc("1", 2), new NumericDocValuesField(SOFT, 1));
      w.flush();
      reopenAndCheck("softUpdateDocument + flush", r, w).close();
    }
  }

  /** tryUpdateDocValue calls changed() which bumps the version itself. */
  public void testControlTryUpdateDocValueThenFlush() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false, true)) {
      DirectoryReader r = DirectoryReader.open(w);
      assertTrue(w.tryUpdateDocValue(r, 0, new NumericDocValuesField("val", 2)) > 0);
      w.flush();
      reopenAndCheck("tryUpdateDocValue + flush", r, w).close();
    }
  }

  public void testControlTryDeleteDocumentThenFlush() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false, true)) {
      DirectoryReader r = DirectoryReader.open(w);
      assertTrue(w.tryDeleteDocument(r, 0) > 0);
      w.flush();
      reopenAndCheck("tryDeleteDocument + flush", r, w).close();
    }
  }

  /** How long the staleness lasts: until the next version bump, e.g. commit(). */
  public void testControlStalenessEndsOnCommit() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false, true)) {
      DirectoryReader r = DirectoryReader.open(w);
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      w.flush();
      DirectoryReader r2 = DirectoryReader.openIfChanged(r, w);
      if (r2 != null) {
        r.close();
        r = r2;
      }
      w.commit();
      reopenAndCheck("update + flush + commit", r, w).close();
    }
  }

  public void testControlStalenessEndsOnAddDocument() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false, true)) {
      DirectoryReader r = DirectoryReader.open(w);
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      w.flush();
      DirectoryReader r2 = DirectoryReader.openIfChanged(r, w);
      if (r2 != null) {
        r.close();
        r = r2;
      }
      w.addDocument(doc("3", 1));
      reopenAndCheck("update + flush + addDocument", r, w).close();
    }
  }

  public void testControlStalenessEndsOnAnotherUnflushedUpdate() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false, true)) {
      DirectoryReader r = DirectoryReader.open(w);
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      w.flush();
      DirectoryReader r2 = DirectoryReader.openIfChanged(r, w);
      if (r2 != null) {
        r.close();
        r = r2;
      }
      w.updateNumericDocValue(new Term("id", "2"), "val", 3);
      reopenAndCheck("update + flush + second update (no flush)", r, w).close();
    }
  }

  /** forceMerge that really merges (2 segments -> 1) commits a merge, which bumps the version. */
  public void testControlForceMergeTwoSegments() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false, false)) {
      w.flush();
      w.addDocument(doc("3", 1));
      DirectoryReader r = DirectoryReader.open(w);
      assertEquals(2, r.leaves().size());
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      w.forceMerge(1);
      reopenAndCheck("update + real forceMerge", r, w).close();
    }
  }
}
