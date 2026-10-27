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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * An NRT reader must not be reported as current, and {@code DirectoryReader.openIfChanged(reader,
 * writer)} must not return null, while the writer holds a change that the reader does not see.
 *
 * <p>In every test the only change after the reader was opened is a doc-values update (or a soft
 * delete made with {@code updateDocValues}), and something then writes it to the existing segment:
 * {@code flush()}, a {@code forceMerge(1)} that has nothing to merge, or an NRT reader opened with
 * {@code writeAllDeletes=true}. A fresh {@code DirectoryReader.open(writer)} is the oracle: it shows
 * the change, so the old reader is stale. The control test is the same sequence without {@code
 * flush()} and passes.
 *
 * <p>Uses public API only. Deterministic: one segment, no merges, no threads.
 */
public class TestOpenIfChangedAfterDocValuesOnlyFlush extends LuceneTestCase {

  private static final String SOFT_DELETES = "soft_delete";

  private static Document doc(String id) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.NO));
    doc.add(new NumericDocValuesField("val", 1));
    return doc;
  }

  /** Two documents (ids 1 and 2, val=1) so that a soft delete never drops the whole segment. */
  private static IndexWriter newWriter(Directory dir, boolean softDeletes) throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    if (softDeletes) {
      iwc.setSoftDeletesField(SOFT_DELETES);
    }
    IndexWriter w = new IndexWriter(dir, iwc);
    w.addDocument(doc("1"));
    w.addDocument(doc("2"));
    return w;
  }

  /** The value of "val" for doc id 1 (docID 0 of the only segment). */
  private static long valOfDoc1(DirectoryReader r) throws IOException {
    assertEquals(1, r.leaves().size());
    NumericDocValues v = r.leaves().get(0).reader().getNumericDocValues("val");
    assertTrue(v.advanceExact(0));
    return v.longValue();
  }

  /**
   * Checks the reopen contract for a stale reader {@code r}: the writer must report a change, and
   * {@code openIfChanged} must return a reader that sees val=2.
   */
  private static void assertReopenSeesUpdate(DirectoryReader r, IndexWriter w) throws IOException {
    try (DirectoryReader fresh = DirectoryReader.open(w)) {
      assertEquals("oracle: a fresh NRT reader sees the update", 2, valOfDoc1(fresh));
    }
    assertEquals("the old reader does not see the update", 1, valOfDoc1(r));
    boolean current = r.isCurrent();
    DirectoryReader r2 = DirectoryReader.openIfChanged(r, w);
    try {
      assertTrue(
          "the reader is stale, but isCurrent()="
              + current
              + " and openIfChanged returned "
              + (r2 == null ? "null" : "a new reader"),
          current == false && r2 != null);
      assertEquals(2, valOfDoc1(r2));
    } finally {
      if (r2 != null) {
        r2.close();
      }
    }
  }

  /** The main case: a numeric doc-values update, then flush(). */
  public void testNumericUpdateThenFlush() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = newWriter(dir, false);
        DirectoryReader r = DirectoryReader.open(w)) {
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      w.flush(); // without this line the test passes (see testControlNumericUpdateNoFlush)
      assertReopenSeesUpdate(r, w);
    }
  }

  /** A soft delete through updateDocValues, then flush(): the deleted doc stays visible. */
  public void testSoftDeleteThenFlush() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = newWriter(dir, true);
        DirectoryReader r = DirectoryReader.open(w)) {
      assertEquals(2, r.numDocs());
      w.updateDocValues(new Term("id", "1"), new NumericDocValuesField(SOFT_DELETES, 1));
      w.flush();
      try (DirectoryReader fresh = DirectoryReader.open(w)) {
        assertEquals("oracle: a fresh NRT reader sees the soft delete", 1, fresh.numDocs());
      }
      boolean current = r.isCurrent();
      DirectoryReader r2 = DirectoryReader.openIfChanged(r, w);
      try {
        assertTrue(
            "the reader is stale, but isCurrent()="
                + current
                + " and openIfChanged returned "
                + (r2 == null ? "null" : "a new reader"),
            current == false && r2 != null);
        assertEquals(1, r2.numDocs());
      } finally {
        if (r2 != null) {
          r2.close();
        }
      }
    }
  }

  /** forceMerge(1) on an index that already has one segment merges nothing, but writes the update. */
  public void testNumericUpdateThenNoOpForceMerge() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(doc("1"));
      w.addDocument(doc("2"));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        w.updateNumericDocValue(new Term("id", "1"), "val", 2);
        w.forceMerge(1);
        assertReopenSeesUpdate(r, w);
      }
    }
  }

  /** A reader opened with writeAllDeletes=true: no flush() is needed. */
  public void testNumericUpdateWithWriteAllDeletesReader() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = newWriter(dir, false);
        DirectoryReader r = DirectoryReader.open(w, true, true)) {
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      boolean current = r.isCurrent();
      DirectoryReader r2 = DirectoryReader.openIfChanged(r, w);
      try {
        assertTrue(
            "the reader is stale, but isCurrent()="
                + current
                + " and openIfChanged returned "
                + (r2 == null ? "null" : "a new reader"),
            r2 != null);
        assertEquals(2, valOfDoc1(r2));
      } finally {
        if (r2 != null) {
          r2.close();
        }
      }
    }
  }

  /**
   * hasUncommittedChanges() may return true when nothing changed, but not false while a change is
   * uncommitted. With a tiny RAM buffer the writer applies the update at once and keeps the new
   * value in memory; it then reports no uncommitted changes, although commit() writes the update.
   */
  public void testHasUncommittedChangesAfterAppliedUpdate() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(doc("1"));
      w.commit();
      assertFalse(w.hasUncommittedChanges());
      w.getConfig().setRAMBufferSizeMB(0.0001); // makes the writer apply the update right away
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      boolean hasUncommittedChanges = w.hasUncommittedChanges();
      w.commit();
      try (DirectoryReader committed = DirectoryReader.open(dir)) {
        assertEquals("oracle: commit() wrote the update, so it was uncommitted", 2, valOfDoc1(committed));
      }
      assertTrue(
          "the update was not committed yet, but hasUncommittedChanges() returned false",
          hasUncommittedChanges);
    }
  }

  /** Control (passes): the same as testNumericUpdateThenFlush without flush(). */
  public void testControlNumericUpdateNoFlush() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = newWriter(dir, false);
        DirectoryReader r = DirectoryReader.open(w)) {
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      assertReopenSeesUpdate(r, w);
    }
  }
}
