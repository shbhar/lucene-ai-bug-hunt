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
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.replicator.nrt.InProcNodes.InProcPrimaryNode;
import org.apache.lucene.replicator.nrt.InProcNodes.InProcReplicaNode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.LuceneTestCase.SuppressFileSystems;
import org.apache.lucene.tests.util.TestUtil;

/**
 * ReplicaNode.commit() captures the current SegmentInfos under the node lock, fsyncs its files,
 * and only then reads the version it stores under {@link Node#VERSION_KEY} from the current
 * searcher. If an NRT point is installed while the fsync runs, the commit stores the newer
 * version together with the older SegmentInfos: VERSION_KEY claims documents the commit does not
 * contain. A primary later started on that commit reports that version from
 * getLastCommitVersion(), which is the point a transaction-log replay resumes from.
 */
@SuppressFileSystems("*")
public class TestReplicaCommitVersionKey extends LuceneTestCase {

  private IndexWriterConfig iwc() {
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    return iwc;
  }

  private static void addDoc(IndexWriter w, String id) throws IOException {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    w.addDocument(doc);
  }

  /** A directory whose next sync() blocks until released (a slow fsync). */
  static final class SlowSyncDirectory extends FilterDirectory {
    final AtomicBoolean armed = new AtomicBoolean();
    final CountDownLatch inSync = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);

    SlowSyncDirectory(Directory in) {
      super(in);
    }

    @Override
    public void sync(Collection<String> names) throws IOException {
      if (armed.compareAndSet(true, false)) {
        inSync.countDown();
        try {
          assertTrue(release.await(60, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      super.sync(names);
    }
  }

  public void testCommitStoresVersionOfLaterNRTPoint() throws Exception {
    PrintStream ps = InProcNodes.log(VERBOSE);
    Path pPath = createTempDir("primary");
    Path rPath = createTempDir("replica");

    IndexWriter w = new IndexWriter(FSDirectory.open(pPath), iwc());
    InProcPrimaryNode primary = new InProcPrimaryNode(w, 0, 1, -1, ps);
    addDoc(w, "d1");
    assertTrue(primary.flushAndRefresh());
    long v1 = primary.getCopyStateVersion();

    SlowSyncDirectory rDir = new SlowSyncDirectory(FSDirectory.open(rPath));
    InProcReplicaNode replica = new InProcReplicaNode(1, rDir, primary, 1, ps);
    replica.syncTo(1, v1);
    assertEquals(v1, replica.getCurrentSearchingVersion());

    // The replica commits; its fsync of the index files is slow.
    rDir.armed.set(true);
    AtomicReference<Throwable> commitFailure = new AtomicReference<>();
    Thread committer =
        new Thread(
            () -> {
              try {
                replica.commit();
              } catch (Throwable t) {
                commitFailure.set(t);
              }
            },
            "replica-commit");
    committer.start();
    assertTrue(rDir.inSync.await(60, TimeUnit.SECONDS));

    // Meanwhile the primary publishes a new point with one more document and the replica
    // installs it (newNRTPoint / finishNRTCopy are not blocked by a running commit).
    addDoc(w, "d2");
    assertTrue(primary.flushAndRefresh());
    long v2 = primary.getCopyStateVersion();
    replica.syncTo(1, v2);
    assertEquals(v2, replica.getCurrentSearchingVersion());

    rDir.release.countDown();
    committer.join();
    assertNull(commitFailure.get());

    // What did the commit persist?
    SegmentInfos committed = SegmentInfos.readLatestCommit(rDir);
    long versionKey = Long.parseLong(committed.getUserData().get(Node.VERSION_KEY));
    Set<String> committedIds;
    try (DirectoryReader r = DirectoryReader.open(rDir)) {
      committedIds = InProcNodes.visibleIds(r);
    }

    primary.setRemoteCloseTimeoutMs(0);
    replica.close();
    primary.close();

    // Start a primary on the replica's commit (promotion) and ask where its commit is up to:
    IndexWriter w2 =
        new IndexWriter(
            FSDirectory.open(rPath), iwc().setOpenMode(IndexWriterConfig.OpenMode.APPEND));
    InProcPrimaryNode promoted = new InProcPrimaryNode(w2, 1, 2, v2 + 1, ps);
    long lastCommitVersion = promoted.getLastCommitVersion();
    promoted.setRemoteCloseTimeoutMs(0);
    promoted.close();

    String msg =
        "replica commit: VERSION_KEY="
            + versionKey
            + " (getLastCommitVersion()="
            + lastCommitVersion
            + " after promotion), but the committed SegmentInfos is version "
            + committed.getVersion()
            + " with documents "
            + committedIds
            + "; point "
            + v2
            + " has documents [d1, d2]";
    // VERSION_KEY must describe the committed point: the version of the SegmentInfos it wrote.
    assertEquals(msg, committed.getVersion(), versionKey);
    // ...in particular it must not claim a version whose documents the commit does not hold.
    if (versionKey >= v2) {
      assertTrue(msg, committedIds.contains("d2"));
    }
  }
}
