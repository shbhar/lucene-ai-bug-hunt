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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * When a merge takes its reader for a segment whose pooled reader has stale live docs,
 * ReadersAndUpdates#getReaderForMerge opens a new reader that shares the pooled reader's core. If
 * opening that reader throws (here: an IOException opening the segment's doc-values-update field
 * infos file), the reference getReaderForMerge took on the pooled reader is never released, so the
 * segment's files stay open after the writer is closed.
 */
public class TestMergeReaderRefreshFailureLeaksReader extends LuceneTestCase {

  private static void addDoc(IndexWriter w, String id) throws IOException {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    doc.add(new NumericDocValuesField("n", 1));
    w.addDocument(doc);
  }

  private static MockDirectoryWrapper newDir(boolean[] armed) {
    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    dir.setRandomIOExceptionRate(0.0);
    dir.setRandomIOExceptionRateOnOpen(0.0);
    dir.failOn(
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper d) throws IOException {
            if (armed[0]
                && callStackContains(ReadersAndUpdates.class, "getReaderForMerge")
                && callStackContains(MockDirectoryWrapper.class, "openInput")) {
              armed[0] = false;
              throw new IOException("simulated I/O error opening a file");
            }
          }
        });
    return dir;
  }

  /** Never merges naturally; merges all segments on commit when asked to. */
  private static class OnDemandMergePolicy extends FilterMergePolicy {
    boolean mergeOnCommit;

    OnDemandMergePolicy() {
      super(new LogDocMergePolicy());
    }

    @Override
    public MergeSpecification findMerges(MergeTrigger t, SegmentInfos infos, MergeContext ctx) {
      return null;
    }

    @Override
    public MergeSpecification findFullFlushMerges(
        MergeTrigger t, SegmentInfos infos, MergeContext ctx) {
      if (mergeOnCommit == false) {
        return null;
      }
      List<SegmentCommitInfo> segments = new ArrayList<>();
      for (SegmentCommitInfo info : infos) {
        if (ctx.getMergingSegments().contains(info) == false) {
          segments.add(info);
        }
      }
      if (segments.size() < 2) {
        return null;
      }
      MergeSpecification spec = new MergeSpecification();
      spec.add(new OneMerge(segments));
      return spec;
    }
  }

  /**
   * Two segments; the first gets a committed doc-values update (so it has a field-infos
   * generation file), its reader is pooled, then a delete makes the pooled reader's live docs
   * stale, so the next merge has to open a refreshed reader for it.
   */
  private static IndexWriter prepare(MockDirectoryWrapper dir, OnDemandMergePolicy mp, long wait)
      throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergeScheduler(new SerialMergeScheduler());
    iwc.setMergePolicy(mp);
    iwc.setMaxFullFlushMergeWaitMillis(wait);
    IndexWriter w = new IndexWriter(dir, iwc);
    addDoc(w, "a");
    addDoc(w, "b");
    addDoc(w, "c");
    w.flush();
    addDoc(w, "d");
    addDoc(w, "e");
    w.flush();
    w.updateNumericDocValue(new Term("id", "a"), "n", 5);
    w.commit();
    DirectoryReader.open(w).close(); // pools the segment readers in the writer
    w.deleteDocuments(new Term("id", "b"));
    return w;
  }

  private static void assertAllFilesClosed(MockDirectoryWrapper dir) throws IOException {
    RuntimeException leak = null;
    try {
      dir.close();
    } catch (RuntimeException e) {
      leak = e; // MockDirectoryWrapper: cannot close: there are still N open files: {...}
    }
    if (leak != null) {
      throw new AssertionError("files left open after the IndexWriter was closed", leak);
    }
  }

  /** Merge-on-commit: the commit fails without a tragic event and the writer is used further. */
  public void testMergeOnCommitReaderFailure() throws Exception {
    boolean[] armed = new boolean[1];
    MockDirectoryWrapper dir = newDir(armed);
    OnDemandMergePolicy mp = new OnDemandMergePolicy();
    IndexWriter w = prepare(dir, mp, 500);
    mp.mergeOnCommit = true;
    armed[0] = true;
    expectThrows(IOException.class, w::commit);
    assertFalse("the failure was not injected", armed[0]);
    assertTrue(w.isOpen());
    assertNull(w.getTragicException());
    w.commit(); // the writer keeps working
    w.close();
    assertAllFilesClosed(dir);
  }

  /** forceMerge: the merge failure is tragic, the writer closes itself. */
  public void testForceMergeReaderFailure() throws Exception {
    boolean[] armed = new boolean[1];
    MockDirectoryWrapper dir = newDir(armed);
    IndexWriter w = prepare(dir, new OnDemandMergePolicy(), 0);
    armed[0] = true;
    expectThrows(IOException.class, () -> w.forceMerge(1));
    assertFalse("the failure was not injected", armed[0]);
    assertNotNull(w.getTragicException());
    assertFalse(w.isOpen());
    assertAllFilesClosed(dir);
  }
}
