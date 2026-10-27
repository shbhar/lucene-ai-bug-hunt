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

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakAction;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakLingering;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * TieredMergePolicy's double setters validate with ordered comparisons only ("v < 2.0", "v <= 0",
 * ...), which NaN passes. setSegmentsPerTier(NaN) then makes findMerges loop forever while it holds
 * the IndexWriter monitor; setForceMergeDeletesPctAllowed(NaN) makes forceMergeDeletes a no-op.
 */
// the writer thread of testSegmentsPerTierNaN cannot be stopped once it spins; keep the report focused
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@ThreadLeakAction({ThreadLeakAction.Action.WARN})
@ThreadLeakLingering(linger = 0)
public class TestTieredMergePolicyNaNSetters extends LuceneTestCase {

  private static void addDoc(IndexWriter w, int id) throws Exception {
    Document doc = new Document();
    doc.add(new StringField("id", Integer.toString(id), Field.Store.NO));
    w.addDocument(doc);
  }

  /** The setters should reject NaN like the out-of-range values they already reject. */
  public void testSettersRejectNaN() {
    TieredMergePolicy tmp = new TieredMergePolicy();
    StringBuilder accepted = new StringBuilder();
    try {
      tmp.setSegmentsPerTier(Double.NaN);
      accepted.append(" setSegmentsPerTier");
    } catch (IllegalArgumentException expected) {
    }
    try {
      tmp.setFloorSegmentMB(Double.NaN);
      accepted.append(" setFloorSegmentMB");
    } catch (IllegalArgumentException expected) {
    }
    try {
      tmp.setDeletesPctAllowed(Double.NaN);
      accepted.append(" setDeletesPctAllowed");
    } catch (IllegalArgumentException expected) {
    }
    try {
      tmp.setForceMergeDeletesPctAllowed(Double.NaN);
      accepted.append(" setForceMergeDeletesPctAllowed");
    } catch (IllegalArgumentException expected) {
    }
    try {
      tmp.setMaxMergedSegmentMB(Double.NaN);
      accepted.append(" setMaxMergedSegmentMB");
    } catch (IllegalArgumentException expected) {
    }
    assertEquals("setters that accepted NaN:" + accepted, "", accepted.toString());
  }

  /** segmentsPerTier=NaN: the first flush that consults the merge policy never returns. */
  public void testSegmentsPerTierNaN() throws Exception {
    TieredMergePolicy tmp = new TieredMergePolicy();
    try {
      tmp.setSegmentsPerTier(Double.NaN); // accepted on main: the check is "v < 2.0"
    } catch (IllegalArgumentException rejected) {
      return; // rejecting NaN up front is a valid fix
    }
    Directory dir = new ByteBuffersDirectory();
    IndexWriter w =
        new IndexWriter(
            dir,
            new IndexWriterConfig(new MockAnalyzer(random()))
                .setMergePolicy(tmp)
                .setMergeScheduler(new SerialMergeScheduler()));
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread indexer =
        new Thread(
            () -> {
              try {
                addDoc(w, 0);
                w.flush(); // findMerges with one segment
                addDoc(w, 1);
                w.flush();
              } catch (Throwable t) {
                failure.set(t);
              }
            },
            "indexer");
    indexer.setDaemon(true);
    indexer.start();
    indexer.join(30_000);
    if (indexer.isAlive()) {
      fail(
          "IndexWriter.flush did not return within 30 s with segmentsPerTier=NaN; indexer stack: "
              + Arrays.toString(indexer.getStackTrace()));
    }
    if (failure.get() != null) {
      throw new AssertionError(failure.get());
    }
    w.close();
    dir.close();
  }

  /** forceMergeDeletesPctAllowed=NaN: forceMergeDeletes silently expunges nothing. */
  public void testForceMergeDeletesPctAllowedNaN() throws Exception {
    assertEquals(
        "forceMergeDeletes left deleted docs with forceMergeDeletesPctAllowed=NaN",
        0,
        deletedDocsLeftAfterForceMergeDeletes(Double.NaN));
  }

  /** Control: the same index with the default 10% is fully expunged. */
  public void testForceMergeDeletesPctAllowedDefaultControl() throws Exception {
    assertEquals(0, deletedDocsLeftAfterForceMergeDeletes(10.0));
  }

  private int deletedDocsLeftAfterForceMergeDeletes(double pct) throws Exception {
    TieredMergePolicy tmp = new TieredMergePolicy();
    try {
      tmp.setForceMergeDeletesPctAllowed(pct); // NaN accepted on main: "v < 0.0 || v > 100.0"
    } catch (IllegalArgumentException rejected) {
      assertTrue(Double.isNaN(pct));
      return 0; // rejecting NaN up front is a valid fix
    }
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(
                dir,
                new IndexWriterConfig(new MockAnalyzer(random()))
                    .setMergePolicy(tmp)
                    .setMergeScheduler(new SerialMergeScheduler()))) {
      // two segments of 20 docs, 3 deleted in each (15%: above 10%, below deletesPctAllowed=20%)
      for (int seg = 0; seg < 2; seg++) {
        for (int i = 0; i < 20; i++) {
          addDoc(w, seg * 20 + i);
        }
        w.flush();
      }
      for (int seg = 0; seg < 2; seg++) {
        for (int i = 0; i < 3; i++) {
          w.deleteDocuments(new Term("id", Integer.toString(seg * 20 + i)));
        }
      }
      w.commit();
      w.forceMergeDeletes();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(34, r.numDocs());
        return r.numDeletedDocs();
      }
    }
  }
}
