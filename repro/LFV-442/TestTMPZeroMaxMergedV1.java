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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * TieredMergePolicy.setMaxMergedSegmentMB accepts 0 ("must be >=0"). With that accepted value, an
 * index with a segment that has many deletes must still commit, close and forceMergeDeletes
 * without an exception. Uses only public API.
 */
public class TestTMPZeroMaxMergedV1 extends LuceneTestCase {

  static final int NUM_DOCS = 100;

  static IndexWriterConfig config(double maxMergedSegmentMB, double deletesPctAllowed) {
    TieredMergePolicy tmp = new TieredMergePolicy();
    tmp.setMaxMergedSegmentMB(maxMergedSegmentMB); // accepted by the setter
    tmp.setDeletesPctAllowed(deletesPctAllowed);
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setMergePolicy(tmp);
    iwc.setMergeScheduler(new SerialMergeScheduler());
    iwc.setMaxBufferedDocs(10 * NUM_DOCS); // one segment per flush
    iwc.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    return iwc;
  }

  /** One committed segment of NUM_DOCS docs, then deletes of the first numDeletes ids. */
  static void indexAndDelete(IndexWriter w, int numDeletes) throws Exception {
    for (int i = 0; i < NUM_DOCS; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
      w.addDocument(doc);
    }
    w.commit(); // no deletes yet: must succeed
    for (int i = 0; i < numDeletes; i++) {
      w.deleteDocuments(new Term("id", Integer.toString(i)));
    }
  }

  public void testSetterAcceptsZero() {
    TieredMergePolicy tmp = new TieredMergePolicy();
    tmp.setMaxMergedSegmentMB(0.0);
    assertEquals(0.0, tmp.getMaxMergedSegmentMB(), 0.0);
  }

  /** Natural merge path: commit() after 50% deletes in the only segment. */
  public void testCommitAfterDeletes() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, config(0.0, 20.0));
      Throwable t = null;
      try {
        indexAndDelete(w, 50);
        w.commit();
      } catch (Throwable e) {
        t = e;
      } finally {
        w.rollback();
      }
      if (t != null) {
        throw new AssertionError("commit() threw with maxMergedSegmentMB=0", t);
      }
    }
  }

  /** close() commits, so it takes the same path. */
  public void testCloseAfterDeletes() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, config(0.0, 20.0));
      Throwable t = null;
      try {
        indexAndDelete(w, 50);
        w.close();
      } catch (Throwable e) {
        t = e;
        w.rollback();
      }
      if (t != null) {
        throw new AssertionError("close() threw with maxMergedSegmentMB=0", t);
      }
    }
  }

  /**
   * Forced-deletes path: deletesPctAllowed=50 so that commit() with 30% deletes is fine, then
   * forceMergeDeletes() (forceMergeDeletesPctAllowed default 10).
   */
  public void testForceMergeDeletes() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, config(0.0, 50.0));
      Throwable t = null;
      String stage = "commit";
      try {
        indexAndDelete(w, 30);
        w.commit();
        stage = "forceMergeDeletes";
        w.forceMergeDeletes();
      } catch (Throwable e) {
        t = e;
      } finally {
        w.rollback();
      }
      if (t != null) {
        throw new AssertionError(stage + "() threw with maxMergedSegmentMB=0", t);
      }
    }
  }
}
