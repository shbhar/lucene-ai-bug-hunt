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
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IORunnable;

public class TestReplicaCommitVersionKey extends LuceneTestCase {

  public void testCommitVersionKeyIsVersionOfCommittedInfos() throws Exception {
    IndexWriter w = new IndexWriter(new ByteBuffersDirectory(), new IndexWriterConfig());
    PrimaryNode primary =
        new PrimaryNode(w, 0, 1, -1, null, null) {
          @Override
          protected void preCopyMergedSegmentFiles(
              SegmentCommitInfo info, Map<String, FileMetaData> files) {}
        };
    AtomicReference<IORunnable> duringFsync = new AtomicReference<>();
    Directory replicaDir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public void sync(Collection<String> names) throws IOException {
            IORunnable r = duringFsync.getAndSet(null);
            if (r != null) {
              r.run();
            }
            super.sync(names);
          }
        };
    Replica replica = new Replica(replicaDir, primary);

    w.addDocument(new Document());
    primary.flushAndRefresh();
    replica.newNRTPoint(1, primary.getCopyStateVersion());
    w.addDocument(new Document());
    primary.flushAndRefresh();
    duringFsync.set(() -> replica.newNRTPoint(1, primary.getCopyStateVersion()));
    replica.commit();

    SegmentInfos committed = SegmentInfos.readLatestCommit(replicaDir);
    replica.close();
    primary.close();
    // expected: VERSION_KEY = version of the committed SegmentInfos; actual: the newer point's
    assertEquals(
        "VERSION_KEY of the replica commit",
        committed.getVersion(),
        Long.parseLong(committed.getUserData().get(Node.VERSION_KEY)));
  }

  private static class Replica extends ReplicaNode {
    private final PrimaryNode primary;

    Replica(Directory dir, PrimaryNode primary) throws IOException {
      super(1, dir, null, null);
      this.primary = primary;
      start(1);
    }

    @Override
    protected CopyJob newCopyJob(
        String reason,
        Map<String, FileMetaData> files,
        Map<String, FileMetaData> prevFiles,
        boolean highPriority,
        CopyJob.OnceDone onceDone)
        throws IOException {
      CopyState state = primary.getCopyState();
      return new CopyJob(reason, state.files(), this, highPriority, onceDone) {
        @Override
        public void finish() throws IOException {
          for (Map.Entry<String, FileMetaData> e : toCopy) {
            dest.getDirectory()
                .copyFrom(primary.getDirectory(), e.getKey(), e.getKey(), IOContext.DEFAULT);
          }
          primary.releaseCopyState(state);
        }

        @Override
        protected CopyOneFile newCopyOneFile(CopyOneFile current) {
          throw new UnsupportedOperationException();
        }

        @Override
        public void start() {}

        @Override
        public void runBlocking() {}

        @Override
        public boolean conflicts(CopyJob other) {
          return false;
        }

        @Override
        public boolean getFailed() {
          return false;
        }

        @Override
        public Set<String> getFileNamesToCopy() {
          return toCopy.stream().map(Map.Entry::getKey).collect(Collectors.toSet());
        }

        @Override
        public Set<String> getFileNames() {
          return files.keySet();
        }

        @Override
        public CopyState getCopyState() {
          return state;
        }

        @Override
        public long getTotalBytesCopied() {
          return 0;
        }

        @Override
        public int compareTo(CopyJob other) {
          return Long.compare(ord, other.ord);
        }
      };
    }

    @Override
    protected void launch(CopyJob job) {
      try {
        job.onceDone.run(job);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    protected void sendNewReplica() {}
  }
}
