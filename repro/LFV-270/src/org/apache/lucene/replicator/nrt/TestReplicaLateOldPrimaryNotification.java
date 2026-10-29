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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestReplicaLateOldPrimaryNotification extends LuceneTestCase {

  public void testLateNotificationFromOlderPrimaryGenKeepsPreCopiedMergedFiles() throws Exception {
    long oldPrimaryGen = 1, newPrimaryGen = 2, oldPrimaryLastVersion = 10;
    IndexWriterConfig iwc = new IndexWriterConfig().setMergeScheduler(new SerialMergeScheduler());
    IndexWriter writer = new IndexWriter(new ByteBuffersDirectory(), iwc);
    try (Primary primary = new Primary(writer, newPrimaryGen, oldPrimaryLastVersion + 1);
        Replica replica = new Replica(new ByteBuffersDirectory(), primary)) {
      writer.addDocument(new Document());
      writer.flush();
      writer.addDocument(new Document());
      primary.flushAndRefresh();
      replica.newNRTPoint(newPrimaryGen, primary.getCopyStateVersion());
      writer.forceMerge(1);
      Set<String> preCopied = new TreeSet<>(replica.pendingMergeFiles);
      assertFalse("the merged segment was not pre-copied", preCopied.isEmpty());

      try {
        replica.newNRTPoint(oldPrimaryGen, oldPrimaryLastVersion);
      } catch (AssertionError | IllegalStateException _) {
      }
      List<String> files = List.of(replica.getDirectory().listAll());
      // expected: the stale call is ignored or rejected, pre-copied files stay; actual: deleted
      assertTrue(
          "pre-copied " + preCopied + " but after the late call the replica has " + files,
          files.containsAll(preCopied));
    }
  }

  private static final class Primary extends PrimaryNode {
    Replica replica;

    Primary(IndexWriter writer, long primaryGen, long forcePrimaryVersion) throws IOException {
      super(writer, 0, primaryGen, forcePrimaryVersion, null, null);
    }

    @Override
    protected void preCopyMergedSegmentFiles(
        SegmentCommitInfo info, Map<String, FileMetaData> files) throws IOException {
      replica.launchPreCopyMerge(new AtomicBoolean(), primaryGen, files);
    }
  }

  private static final class Replica extends ReplicaNode {
    final Primary primary;

    Replica(Directory dir, Primary primary) throws IOException {
      super(1, dir, null, null);
      this.primary = primary;
      primary.replica = this;
      start(primary.getPrimaryGen());
    }

    @Override
    protected CopyJob newCopyJob(
        String reason,
        Map<String, FileMetaData> files,
        Map<String, FileMetaData> prevFiles,
        boolean highPriority,
        CopyJob.OnceDone onceDone)
        throws IOException {
      CopyState copyState = files == null ? primary.getCopyState() : null;
      if (copyState != null) {
        files = copyState.files();
      }
      return new DirectoryCopyJob(reason, files, this, highPriority, onceDone, copyState);
    }

    @Override
    protected void launch(CopyJob job) {
      try {
        job.runBlocking();
        job.onceDone.run(job);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    protected void sendNewReplica() {}
  }

  /** Copies the missing files straight from the primary's directory, on the calling thread. */
  private static final class DirectoryCopyJob extends CopyJob {
    private final CopyState copyState;

    DirectoryCopyJob(
        String reason,
        Map<String, FileMetaData> files,
        Replica dest,
        boolean highPriority,
        OnceDone onceDone,
        CopyState copyState)
        throws IOException {
      super(reason, files, dest, highPriority, onceDone);
      this.copyState = copyState;
    }

    private Primary primary() {
      return ((Replica) dest).primary;
    }

    @Override
    public void start() {}

    @Override
    public void runBlocking() throws IOException {
      for (Map.Entry<String, FileMetaData> ent : toCopy) {
        dest.getDirectory()
            .copyFrom(primary().getDirectory(), ent.getKey(), ent.getKey(), IOContext.DEFAULT);
      }
    }

    @Override
    public void finish() throws IOException {
      if (copyState != null) {
        primary().releaseCopyState(copyState);
      }
    }

    @Override
    protected CopyOneFile newCopyOneFile(CopyOneFile current) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean conflicts(CopyJob other) {
      return false;
    }

    @Override
    public boolean getFailed() {
      return exc != null;
    }

    @Override
    public Set<String> getFileNamesToCopy() {
      Set<String> names = new HashSet<>();
      for (Map.Entry<String, FileMetaData> ent : toCopy) {
        names.add(ent.getKey());
      }
      return names;
    }

    @Override
    public Set<String> getFileNames() {
      return files.keySet();
    }

    @Override
    public CopyState getCopyState() {
      return copyState;
    }

    @Override
    public long getTotalBytesCopied() {
      return 0;
    }

    @Override
    public int compareTo(CopyJob other) {
      return Long.compare(ord, other.ord);
    }
  }
}
