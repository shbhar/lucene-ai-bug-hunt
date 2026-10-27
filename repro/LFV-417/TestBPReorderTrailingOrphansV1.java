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
package org.apache.lucene.misc.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Independent repro (public API only): a segment whose last block lost its parent document (the
 * parent was deleted and a merge dropped it) cannot be merged by BPReorderingMergePolicy.
 *
 * <p>Index: 20 blocks of 4 children + 1 parent = 100 docs, parent field set, default codec, serial
 * merges. All operations are public IndexWriter calls.
 */
public class TestBPReorderTrailingOrphansV1 extends LuceneTestCase {

  private static final String PARENT = "_parent";
  private static final int BLOCKS = 20;
  private static final int CHILDREN = 4;

  private static List<Document> block(int b) {
    List<Document> docs = new ArrayList<>();
    for (int c = 0; c < CHILDREN; c++) {
      Document d = new Document();
      d.add(new StringField("id", "b" + b + "c" + c, Field.Store.NO));
      d.add(new StringField("type", "child", Field.Store.NO));
      docs.add(d);
    }
    Document p = new Document();
    p.add(new StringField("id", "b" + b + "p", Field.Store.NO));
    p.add(new StringField("type", "parent", Field.Store.NO));
    docs.add(p);
    return docs;
  }

  private static IndexWriterConfig config(MergePolicy mp) {
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setParentField(PARENT);
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(mp);
    iwc.setMergeScheduler(new SerialMergeScheduler());
    return iwc;
  }

  private static MergePolicy bpPolicy() {
    return new BPReorderingMergePolicy(new LogDocMergePolicy(), new BPIndexReorderer());
  }

  private static void addBlocks(IndexWriter w) throws IOException {
    for (int b = 0; b < BLOCKS; b++) {
      w.addDocuments(block(b));
    }
    w.commit();
  }

  /** Setup check: one segment of 99 docs whose last doc has no parent marker. */
  private static void assertTrailingOrphans(Directory dir) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals("SETUP: expected one segment", 1, r.leaves().size());
      LeafReader leaf = r.leaves().get(0).reader();
      assertEquals("SETUP: maxDoc", BLOCKS * (CHILDREN + 1) - 1, leaf.maxDoc());
      assertEquals("SETUP: no deletions left", leaf.maxDoc(), leaf.numDocs());
      assertEquals("SETUP: parent field", PARENT, leaf.getFieldInfos().getParentField());
      NumericDocValues parents = leaf.getNumericDocValues(PARENT);
      assertFalse(
          "SETUP: last doc must be an orphan child", parents.advanceExact(leaf.maxDoc() - 1));
    }
    // The index with trailing orphan children is a valid index according to CheckIndex.
    TestUtil.checkIndex(dir);
  }

  private static String describe(Throwable t) {
    StringBuilder sb = new StringBuilder();
    for (Throwable c = t; c != null; c = c.getCause()) {
      sb.append(" <- ").append(c);
      StackTraceElement[] st = c.getStackTrace();
      if (st.length > 0) {
        sb.append(" at ").append(st[0]);
      }
    }
    return sb.toString();
  }

  /** The step under test: delete one child elsewhere, then a forced (reordering) merge. */
  private static void deleteOtherChildAndForceMerge(IndexWriter w) throws IOException {
    w.deleteDocuments(new Term("id", "b0c0"));
    Throwable thrown = null;
    try {
      w.forceMerge(1);
    } catch (Throwable t) {
      thrown = t;
    }
    assertNull("the forced merge failed:" + (thrown == null ? "" : describe(thrown)), thrown);
    assertTrue("the writer must stay open", w.isOpen());
    assertNull("no tragic exception expected", w.getTragicException());
    w.commit();
    try (DirectoryReader r = DirectoryReader.open(w)) {
      assertEquals(BLOCKS * (CHILDREN + 1) - 2, r.numDocs());
    }
  }

  /**
   * BPReorderingMergePolicy is the only merge policy ever used. The first forced-deletes merge is
   * reordered while the deleted parent still marks its block, and it drops that parent, so the
   * merged segment ends with orphan children. The next reordering merge of that segment fails.
   */
  public void testBPPolicyOnlySingleWriter() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, config(bpPolicy()));
      try {
        addBlocks(w);
        w.deleteDocuments(new Term("id", "b" + (BLOCKS - 1) + "p")); // parent of the last block
        w.forceMergeDeletes();
        w.commit();
        assertTrue("SETUP: first merge must succeed", w.isOpen());
        assertTrailingOrphans(dir);
        deleteOtherChildAndForceMerge(w);
      } finally {
        if (w.isOpen()) {
          w.close();
        }
      }
    }
  }

  /** As in the claim: a plain merge policy makes the segment, a later BP session merges it. */
  public void testTwoSessionsPlainThenBP() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config(new LogDocMergePolicy()))) {
        addBlocks(w);
        w.deleteDocuments(new Term("id", "b" + (BLOCKS - 1) + "p"));
        w.forceMergeDeletes();
        w.commit();
      }
      assertTrailingOrphans(dir);
      IndexWriter w = new IndexWriter(dir, config(bpPolicy()));
      try {
        deleteOtherChildAndForceMerge(w);
      } finally {
        if (w.isOpen()) {
          w.close();
        }
      }
    }
  }

  /** Control: the same operations without reordering succeed (expected to pass everywhere). */
  public void testControlPlainPolicy() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config(new LogDocMergePolicy()))) {
        addBlocks(w);
        w.deleteDocuments(new Term("id", "b" + (BLOCKS - 1) + "p"));
        w.forceMergeDeletes();
        w.commit();
        assertTrailingOrphans(dir);
        deleteOtherChildAndForceMerge(w);
      }
    }
  }
}
