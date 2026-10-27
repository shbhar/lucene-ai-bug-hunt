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
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.replicator.nrt.InProcNodes.InProcCopyJob;
import org.apache.lucene.replicator.nrt.InProcNodes.InProcPrimaryNode;
import org.apache.lucene.replicator.nrt.InProcNodes.InProcReplicaNode;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.LuceneTestCase.SuppressFileSystems;
import org.apache.lucene.tests.util.TestUtil;

/**
 * The OnceDone of a merge pre-copy job (ReplicaNode.launchPreCopyMerge) does its "has the primary
 * changed / is the file already NRT-visible" checks and the final rename inside {@code
 * synchronized (this)}, but {@code this} is the anonymous OnceDone object, not the ReplicaNode. So
 * newNRTPoint()/maybeNewPrimary() can switch to a new primary between the check and the rename,
 * and the old primary's pre-copied merged files are then added to pendingMergeFiles after
 * maybeNewPrimary() has discarded them.
 */
@SuppressFileSystems("*")
public class TestMergePreCopyOnceDoneMonitor extends LuceneTestCase {

  private IndexWriterConfig iwc() {
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    LogDocMergePolicy mp = new LogDocMergePolicy();
    mp.setMergeFactor(1000); // no natural merges; only the explicit forceMerge below
    iwc.setMergePolicy(mp);
    iwc.setMergeScheduler(new SerialMergeScheduler());
    return iwc;
  }

  private static void addDoc(IndexWriter w, String id) throws IOException {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    w.addDocument(doc);
  }

  public void testPrimaryChangeDuringMergePreCopyFinish() throws Exception {
    PrintStream ps = InProcNodes.log(VERBOSE);
    Path p1Path = createTempDir("p1");
    Path aPath = createTempDir("replicaA");
    Path bPath = createTempDir("replicaB");

    IndexWriter w1 = new IndexWriter(FSDirectory.open(p1Path), iwc());
    InProcPrimaryNode p1 = new InProcPrimaryNode(w1, 0, 1, -1, ps);
    addDoc(w1, "a");
    w1.flush();
    addDoc(w1, "b");
    assertTrue(p1.flushAndRefresh());
    long v1 = p1.getCopyStateVersion();
    InProcReplicaNode ra = new InProcReplicaNode(1, FSDirectory.open(aPath), p1, 1, ps);
    InProcReplicaNode rb = new InProcReplicaNode(2, FSDirectory.open(bPath), p1, 1, ps);
    ra.syncTo(1, v1);
    rb.syncTo(1, v1);

    // P1 merges its two segments; its merged-segment warmer asks replica B to pre-copy the merged
    // files (as SimplePrimaryNode does over the network) and waits for the pre-copy to finish.
    AtomicReference<InProcCopyJob> mergeJob = new AtomicReference<>();
    CountDownLatch mergeJobLaunched = new CountDownLatch(1);
    CountDownLatch mergeJobDone = new CountDownLatch(1);
    AtomicBoolean finished = new AtomicBoolean();
    Set<String> mergedFiles = new TreeSet<>();
    p1.preCopyHook =
        (info, files) -> {
          mergedFiles.addAll(files.keySet());
          mergeJob.set((InProcCopyJob) rb.launchPreCopyMerge(finished, 1, files));
          mergeJobLaunched.countDown();
          try {
            assertTrue(mergeJobDone.await(60, TimeUnit.SECONDS));
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        };
    AtomicReference<Throwable> mergeFailure = new AtomicReference<>();
    Thread merger =
        new Thread(
            () -> {
              try {
                w1.forceMerge(1);
              } catch (Throwable t) {
                mergeFailure.set(t);
              }
            },
            "p1-merge");
    merger.start();
    assertTrue(mergeJobLaunched.await(60, TimeUnit.SECONDS));
    InProcCopyJob mj = mergeJob.get();
    while (mj.visit() == false) {} // all merged files copied to temp files

    // Replica A is promoted to P2 (primaryGen=2) meanwhile.
    ra.commit();
    ra.close();
    IndexWriter w2 =
        new IndexWriter(
            FSDirectory.open(aPath), iwc().setOpenMode(IndexWriterConfig.OpenMode.APPEND));
    InProcPrimaryNode p2 = new InProcPrimaryNode(w2, 1, 2, p1.getCopyStateVersion() + 1, ps);
    long vP2 = p2.getCopyStateVersion(); // P2's first point (P1's v1 content, new version)

    // The copy thread runs the merge job's OnceDone. It passes the "primary changed?" check and
    // enters job.finish() (the renames), which is slow here.
    CountDownLatch inFinish = new CountDownLatch(1);
    CountDownLatch releaseFinish = new CountDownLatch(1);
    mj.beforeFinish =
        job -> {
          inFinish.countDown();
          try {
            assertTrue(releaseFinish.await(60, TimeUnit.SECONDS));
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        };
    AtomicReference<Throwable> onceDoneFailure = new AtomicReference<>();
    Thread copyThread =
        new Thread(
            () -> {
              try {
                rb.runJob(mj);
              } catch (Throwable t) {
                onceDoneFailure.set(t);
              }
            },
            "rb-copy-jobs");
    copyThread.start();
    assertTrue(inFinish.await(60, TimeUnit.SECONDS));

    // Meanwhile replica B receives P2's NRT point (primaryGen=2). If the OnceDone held the
    // ReplicaNode monitor this call would wait for it; it does not.
    rb.primary = p2;
    AtomicReference<Throwable> nrtFailure = new AtomicReference<>();
    AtomicReference<CopyJob> nrtJob = new AtomicReference<>();
    CountDownLatch nrtReturned = new CountDownLatch(1);
    Thread receiver =
        new Thread(
            () -> {
              try {
                nrtJob.set(rb.newNRTPoint(2, vP2));
              } catch (Throwable t) {
                nrtFailure.set(t);
              } finally {
                nrtReturned.countDown();
              }
            },
            "rb-recv");
    receiver.start();
    boolean newNRTPointRanDuringOnceDone = nrtReturned.await(5, TimeUnit.SECONDS);
    releaseFinish.countDown();
    copyThread.join();
    receiver.join();
    mergeJobDone.countDown();
    merger.join();
    assertNull(nrtFailure.get());
    assertNull(onceDoneFailure.get());
    assertNull(mergeFailure.get());
    if (nrtJob.get() != null) {
      rb.runJob((InProcCopyJob) nrtJob.get());
    }

    long gen = rb.lastPrimaryGen;
    Set<String> pending = new TreeSet<>(rb.pendingMergeFiles);
    Set<String> onDisk = new HashSet<>(Arrays.asList(rb.getDirectory().listAll()));
    Set<String> staleOnDisk = new TreeSet<>(mergedFiles);
    staleOnDisk.retainAll(onDisk);
    staleOnDisk.removeAll(rb.lastNRTFiles);

    p1.setRemoteCloseTimeoutMs(0);
    p2.setRemoteCloseTimeoutMs(0);
    rb.close();
    p2.close();
    p1.close();

    String msg =
        "newNRTPoint ran while the merge OnceDone was inside its synchronized block: "
            + newNRTPointRanDuringOnceDone
            + "; replica B now follows primaryGen "
            + gen
            + " but pendingMergeFiles="
            + pending
            + " and P1's pre-copied merged files still on disk: "
            + staleOnDisk;
    assertEquals(2, gen);
    // maybeNewPrimary discards pre-copied merged segments of the old primary; none may survive
    assertTrue(msg, pending.isEmpty());
    assertTrue(msg, staleOnDisk.isEmpty());
  }
}
