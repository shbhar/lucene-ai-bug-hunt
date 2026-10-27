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

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
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
 * A replica that is still copying an NRT point from the old primary when it is told about a new
 * primary's point carries the old primary's already-copied temp files over into the new copy job,
 * by file name only. After a primary change the same file name can hold different content (here
 * the live-docs file {@code _0_1.liv}), so the replica opens the new primary's point with the old
 * primary's bytes: same version as the new primary, different documents.
 */
@SuppressFileSystems("*")
public class TestNRTCopyAcrossPrimaryChange extends LuceneTestCase {

  private IndexWriterConfig iwc() {
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    return iwc;
  }

  private static void addDoc(IndexWriter w, String id) throws Exception {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    w.addDocument(doc);
  }

  public void testInFlightCopyFromOldPrimaryIsReusedForNewPrimary() throws Exception {
    runScenario(false);
  }

  /**
   * Same, but the deposed primary dies before replica B hears about P2 (B's copy job has not yet
   * noticed: it is between two files or blocked on a dead connection).
   */
  public void testOldPrimaryDiesBeforeReplicaLearnsOfNewPrimary() throws Exception {
    runScenario(true);
  }

  private void runScenario(boolean oldPrimaryDies) throws Exception {
    PrintStream ps = InProcNodes.log(VERBOSE);
    Path p1Path = createTempDir("p1");
    Path aPath = createTempDir("replicaA");
    Path bPath = createTempDir("replicaB");

    // Primary P1, primaryGen=1: two segments, _0 = {a0, b0} and _1 = {a1, b1}
    IndexWriter w1 = new IndexWriter(FSDirectory.open(p1Path), iwc());
    InProcPrimaryNode p1 = new InProcPrimaryNode(w1, 0, 1, -1, ps);
    addDoc(w1, "a0");
    addDoc(w1, "b0");
    w1.flush();
    addDoc(w1, "a1");
    addDoc(w1, "b1");
    assertTrue(p1.flushAndRefresh());
    long v1 = p1.getCopyStateVersion();

    // Two replicas, both fully synced to version v1
    InProcReplicaNode ra = new InProcReplicaNode(1, FSDirectory.open(aPath), p1, 1, ps);
    InProcReplicaNode rb = new InProcReplicaNode(2, FSDirectory.open(bPath), p1, 1, ps);
    ra.syncTo(1, v1);
    rb.syncTo(1, v1);
    assertEquals(v1, ra.getCurrentSearchingVersion());
    assertEquals(v1, rb.getCurrentSearchingVersion());

    // The controller loses contact with P1 and promotes replica A (a most current replica: both
    // replicas are at v1 and nothing is being copied) to primary P2, primaryGen=2, with its version
    // forced past the last version P1 reported (v1).
    ra.commit();
    ra.close();
    IndexWriter w2 =
        new IndexWriter(
            FSDirectory.open(aPath), iwc().setOpenMode(IndexWriterConfig.OpenMode.APPEND));
    InProcPrimaryNode p2 = new InProcPrimaryNode(w2, 1, 2, v1 + 1, ps);

    // P1 is deposed but still running (e.g. on the other side of a partition): it deletes a0 and a1
    // and publishes v2 (new files _0_1.liv and _1_1.liv). Replica B, which has not heard of P2
    // yet, starts copying v2 and has copied one of the two .liv files.
    w1.deleteDocuments(new Term("id", "a0"), new Term("id", "a1"));
    assertTrue(p1.flushAndRefresh());
    long v2 = p1.getCopyStateVersion();
    InProcCopyJob j1 = (InProcCopyJob) rb.newNRTPoint(1, v2);
    assertNotNull(j1);
    assertEquals(2, j1.getFileNamesToCopy().size());
    assertFalse(j1.visit()); // one whole file copied, one to go
    Set<String> copiedFromP1 = j1.copiedFileNames();
    assertEquals(1, copiedFromP1.size());

    // P2 deletes b0 and b1 and publishes v3: same file names _0_1.liv and _1_1.liv as P1's v2
    // (same segments, same delete generation, same delete count), different documents deleted.
    w2.deleteDocuments(new Term("id", "b0"), new Term("id", "b1"));
    assertTrue(p2.flushAndRefresh());
    long v3 = p2.getCopyStateVersion();
    assertTrue(v3 > v1);
    Set<String> expected = InProcNodes.visibleIds(p2);
    assertEquals(new TreeSet<>(Set.of("a0", "a1")), expected);

    if (oldPrimaryDies) {
      p1.setRemoteCloseTimeoutMs(0);
      p1.close();
    }

    // Replica B now hears about the new primary's point (higher primaryGen) and syncs to it.
    rb.primary = p2;
    InProcCopyJob j2 = (InProcCopyJob) rb.newNRTPoint(2, v3);
    assertNotNull(j2);
    assertTrue("old job must be cancelled", j1.getFailed());
    rb.runJob(j1); // the old job's OnceDone (it was cancelled by the new job)
    rb.runJob(j2);
    assertEquals(v3, rb.getCurrentSearchingVersion());

    Set<String> actual = InProcNodes.visibleIds(rb);

    // Persist the replica's point and check the committed index on its own.
    rb.commit();
    p2.setRemoteCloseTimeoutMs(0);
    rb.close();
    p2.close();
    if (oldPrimaryDies == false) {
      p1.setRemoteCloseTimeoutMs(0);
      p1.close();
    }
    Set<String> committed;
    boolean checkIndexClean;
    try (Directory d = FSDirectory.open(bPath)) {
      try (DirectoryReader r = DirectoryReader.open(d)) {
        committed = InProcNodes.visibleIds(r);
      }
      checkIndexClean = TestUtil.checkIndex(d).clean;
    }

    String msg =
        "replica B is at the new primary's version "
            + v3
            + " but shows different documents (committed: "
            + committed
            + ", CheckIndex clean="
            + checkIndexClean
            + "); file(s) carried over from the old primary's in-flight copy: "
            + copiedFromP1;
    assertEquals(msg, expected, actual);
    assertEquals(msg, expected, committed);
  }
}
