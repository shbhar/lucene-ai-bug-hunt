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
package org.apache.lucene.replicator.nrt;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.replicator.nrt.InProcNodes.InProcCopyJob;
import org.apache.lucene.replicator.nrt.InProcNodes.InProcPrimaryNode;
import org.apache.lucene.replicator.nrt.InProcNodes.InProcReplicaNode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.LuceneTestCase.SuppressFileSystems;
import org.apache.lucene.tests.util.TestUtil;

/**
 * The old primary P1 (primaryGen=1) is closed BEFORE the new primary P2 (primaryGen=2) is started,
 * as Lucene's own test controllers do, and replica B follows P2. P2 merges and pre-copies the
 * merged segment to B (ReplicaNode.launchPreCopyMerge), then publishes the merged point. A
 * notification that P1 sent before it closed reaches B late: newNRTPoint(1, olderVersion). Its
 * version is older than B's, so nothing is copied for it. B may ignore the late notification or
 * reject it with an exception, but it should keep following P2: keep its primary generation, keep
 * P2's pre-copied merged files, and install P2's merged point without copying the merged segment
 * again.
 *
 * <ul>
 *   <li>DURING_COPY: the late notification arrives while B copies P2's merged point.
 *   <li>BEFORE_MERGED_POINT: it arrives after the pre-copy, before P2 publishes the merged point.
 *   <li>NONE: control, no late notification.
 * </ul>
 */
@SuppressFileSystems("*")
public class TestLateNotificationDropsPreCopiedMerge extends LuceneTestCase {

  enum Late {
    NONE,
    DURING_COPY,
    BEFORE_MERGED_POINT
  }

  private IndexWriterConfig iwc(boolean merges) {
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    if (merges) {
      // No natural merges for a handful of segments; forceMerge(1) merges them, in this thread.
      LogDocMergePolicy mp = new LogDocMergePolicy();
      mp.setMergeFactor(1000);
      iwc.setMergePolicy(mp);
      iwc.setMergeScheduler(new SerialMergeScheduler());
    } else {
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    }
    return iwc;
  }

  private static void addDoc(IndexWriter w, String id) throws IOException {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    w.addDocument(doc);
  }

  private static boolean exists(Directory dir, String name) throws IOException {
    return Arrays.asList(dir.listAll()).contains(name);
  }

  public void testLateOldGenerationNotificationWhileCopyingMergedPoint() throws Exception {
    runScenario(Late.DURING_COPY);
  }

  public void testLateOldGenerationNotificationBeforeMergedPoint() throws Exception {
    runScenario(Late.BEFORE_MERGED_POINT);
  }

  public void testControlWithoutLateNotification() throws Exception {
    runScenario(Late.NONE);
  }

  private void runScenario(Late late) throws Exception {
    PrintStream ps = InProcNodes.log(VERBOSE);
    Path p1Path = createTempDir("p1");
    Path aPath = createTempDir("replicaA");
    Path bPath = createTempDir("replicaB");

    // P1 (primaryGen=1) with replicas A and B.
    IndexWriter w1 = new IndexWriter(FSDirectory.open(p1Path), iwc(false));
    InProcPrimaryNode p1 = new InProcPrimaryNode(w1, 0, 1, -1, ps);
    addDoc(w1, "d1");
    assertTrue(p1.flushAndRefresh());
    long v1 = p1.getCopyStateVersion();
    InProcReplicaNode ra = new InProcReplicaNode(1, FSDirectory.open(aPath), p1, 1, ps);
    InProcReplicaNode rb = new InProcReplicaNode(2, FSDirectory.open(bPath), p1, 1, ps);
    ra.syncTo(1, v1);
    rb.syncTo(1, v1);

    // P1 publishes v2; its notification to B is still in flight when P1 goes away.
    addDoc(w1, "d2");
    assertTrue(p1.flushAndRefresh());
    long v2 = p1.getCopyStateVersion();
    p1.setRemoteCloseTimeoutMs(0);
    p1.close();

    // Only now is A promoted to P2 (primaryGen=2), past every version P1 may have published.
    ra.commit();
    ra.close();
    IndexWriter w2 =
        new IndexWriter(
            FSDirectory.open(aPath), iwc(true).setOpenMode(IndexWriterConfig.OpenMode.APPEND));
    InProcPrimaryNode p2 = new InProcPrimaryNode(w2, 1, 2, v2 + 1, ps);
    rb.primary = p2;
    rb.syncTo(2, p2.getCopyStateVersion());
    assertEquals(2, rb.lastPrimaryGen);

    // P2 flushes two more segments; B follows.
    addDoc(w2, "p2a");
    assertTrue(p2.flushAndRefresh());
    rb.syncTo(2, p2.getCopyStateVersion());
    addDoc(w2, "p2b");
    assertTrue(p2.flushAndRefresh());
    rb.syncTo(2, p2.getCopyStateVersion());
    long vBefore = rb.getCurrentSearchingVersion();
    assertEquals(p2.getCopyStateVersion(), vBefore);
    assertTrue(vBefore > v2);

    // P2 merges. Before it cuts over, the merged segment is pre-copied to B, as SimplePrimaryNode
    // does with CMD_PRE_COPY_MERGE -> ReplicaNode.launchPreCopyMerge.
    Set<String> preCopied = new TreeSet<>();
    p2.preCopyHook =
        (info, files) -> {
          AtomicBoolean finished = new AtomicBoolean();
          CopyJob mergeJob = rb.launchPreCopyMerge(finished, p2.getPrimaryGen(), files);
          rb.runJob((InProcCopyJob) mergeJob);
          assertTrue(finished.get());
          preCopied.addAll(files.keySet());
        };
    w2.forceMerge(1);
    p2.preCopyHook = null;
    assertFalse("no merged segment was pre-copied", preCopied.isEmpty());
    assertTrue(rb.pendingMergeFiles.containsAll(preCopied));

    CopyJob staleJob = null;
    // Rejecting the late notification (returning null or throwing) is acceptable; changing the
    // replica's state for it is not.
    Throwable rejection = null;
    Set<String> missing = new TreeSet<>();
    long genAfterLate = -1;

    if (late == Late.BEFORE_MERGED_POINT) {
      // The late notification from the dead P1 (primaryGen=1, version v2 < B's version) arrives.
      try {
        staleJob = rb.newNRTPoint(1, v2);
      } catch (Throwable e) {
        rejection = e; // e.g. -ea: the assert in ReplicaNode.maybeNewPrimary
      }
      for (String f : preCopied) {
        if (exists(rb.getDirectory(), f) == false) {
          missing.add(f);
        }
      }
      genAfterLate = rb.lastPrimaryGen;
    }

    // P2 publishes the merged point; B starts copying it.
    assertTrue(p2.flushAndRefresh());
    long vMerged = p2.getCopyStateVersion();
    Set<String> p2Ids = InProcNodes.visibleIds(p2);
    InProcCopyJob job = (InProcCopyJob) rb.newNRTPoint(2, vMerged);
    assertNotNull(job);
    Set<String> toCopy = job.getFileNamesToCopy();
    Set<String> recopied = new TreeSet<>(preCopied);
    recopied.retainAll(toCopy);

    if (late == Late.DURING_COPY) {
      // The late notification from the dead P1 (primaryGen=1, version v2 < B's version) arrives.
      try {
        staleJob = rb.newNRTPoint(1, v2);
      } catch (Throwable e) {
        rejection = e; // e.g. -ea: the assert in ReplicaNode.maybeNewPrimary
      }
    }
    if (late != Late.BEFORE_MERGED_POINT) {
      for (String f : preCopied) {
        if (exists(rb.getDirectory(), f) == false) {
          missing.add(f);
        }
      }
      genAfterLate = rb.lastPrimaryGen;
    }

    // B finishes copying P2's merged point (the launcher thread's work).
    Throwable copyFailure = null;
    try {
      rb.runJob(job);
    } catch (Throwable t) {
      copyFailure = t;
    }
    long bytesCopiedForMergedPoint = job.getTotalBytesCopied();
    long versionAfter = rb.getCurrentSearchingVersion();
    Set<String> idsAfter = InProcNodes.visibleIds(rb);

    // Aftermath: the application commits the replica, then P2 publishes its next point.
    Throwable commitFailure = null;
    try {
      rb.commit();
    } catch (Throwable t) {
      commitFailure = t;
    }
    addDoc(w2, "p2c");
    assertTrue(p2.flushAndRefresh());
    long vNext = p2.getCopyStateVersion();
    Set<String> p2IdsNext = InProcNodes.visibleIds(p2);
    Throwable nextSyncFailure = null;
    try {
      rb.syncTo(2, vNext);
    } catch (Throwable t) {
      nextSyncFailure = t;
    }
    long versionNext = rb.getCurrentSearchingVersion();
    Set<String> idsNext = InProcNodes.visibleIds(rb);
    long genNext = rb.lastPrimaryGen;
    Throwable commitNextFailure = null;
    try {
      rb.commit();
    } catch (Throwable t) {
      commitNextFailure = t;
    }

    Throwable closeFailure = null;
    p2.setRemoteCloseTimeoutMs(0);
    for (Node n : List.<Node>of(rb, p2)) {
      try {
        n.close();
      } catch (Throwable t) {
        if (closeFailure == null) {
          closeFailure = t;
        }
      }
    }

    String msg =
        "late="
            + late
            + ": B followed P2 at version "
            + vBefore
            + ", pre-copied merged files "
            + preCopied
            + "; late P1 notification: job="
            + staleJob
            + " rejected with="
            + rejection
            + " lastPrimaryGen after it="
            + genAfterLate
            + " pre-copied files then missing="
            + missing
            + "; P2's merged point (version "
            + vMerged
            + "): files to copy "
            + toCopy
            + " (pre-copied files copied again: "
            + recopied
            + ", bytes copied "
            + bytesCopiedForMergedPoint
            + "); after that copy B serves version "
            + versionAfter
            + " with "
            + idsAfter
            + " (P2 has "
            + p2Ids
            + "); copy failure="
            + copyFailure
            + (copyFailure != null && copyFailure.getCause() != null
                ? " caused by " + copyFailure.getCause()
                : "")
            + "; commit right after: failure="
            + commitFailure
            + "; P2's next point (version "
            + vNext
            + ", "
            + p2IdsNext
            + "): sync failure="
            + nextSyncFailure
            + ", B then serves version "
            + versionNext
            + " with "
            + idsNext
            + ", lastPrimaryGen="
            + genNext
            + ", commit failure="
            + commitNextFailure
            + "; close failure="
            + closeFailure;
    System.out.println("TIEBREAK-PROBE " + msg);
    assertEquals(msg, 2, genAfterLate);
    assertTrue(msg, missing.isEmpty());
    assertTrue(msg, recopied.isEmpty());
    assertNull(msg, copyFailure);
    assertEquals(msg, vMerged, versionAfter);
    assertEquals(msg, p2Ids, idsAfter);
    assertNull(msg, commitFailure);
    assertNull(msg, nextSyncFailure);
    assertEquals(msg, vNext, versionNext);
    assertEquals(msg, p2IdsNext, idsNext);
    assertNull(msg, commitNextFailure);
    assertNull(msg, closeFailure);
  }
}
