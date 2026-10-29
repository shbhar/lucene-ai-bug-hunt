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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMergePreCopyPrimaryChange extends LuceneTestCase {

  public void testPrimaryChangeWhileMergePreCopyInstalls() throws Exception {
    try (Replica replica = new Replica(new ByteBuffersDirectory())) {
      replica.start(1);
      replica.launchPreCopyMerge(
          new AtomicBoolean(),
          1,
          Map.of("_0.si", new FileMetaData(new byte[0], new byte[0], 0, 0)));
      replica.job.onceDone.run(replica.job);
      replica.newPrimaryThread.join();
      // expected: newNRTPoint(2) discards the old primary's pre-copied files; actual: still pending
      assertEquals(
          "pendingMergeFiles at primaryGen " + replica.lastPrimaryGen,
          Set.of(),
          replica.pendingMergeFiles);
    }
  }

  private static final class Replica extends ReplicaNode {
    CopyJob job;
    Thread newPrimaryThread;

    Replica(Directory dir) throws IOException {
      super(0, dir, null, null);
    }

    @Override
    protected CopyJob newCopyJob(
        String reason,
        Map<String, FileMetaData> files,
        Map<String, FileMetaData> prevFiles,
        boolean highPriority,
        CopyJob.OnceDone onceDone)
        throws IOException {
      return new InstallJob(reason, files, this, onceDone);
    }

    @Override
    protected void launch(CopyJob job) {
      this.job = job;
    }

    @Override
    protected void sendNewReplica() {}
  }

  private static final class InstallJob extends CopyJob {
    InstallJob(String reason, Map<String, FileMetaData> files, Replica dest, OnceDone onceDone)
        throws IOException {
      super(reason, files, dest, false, onceDone);
    }

    @Override
    public void finish() throws IOException {
      Replica replica = (Replica) dest;
      replica.newPrimaryThread =
          new Thread(
              () -> {
                try {
                  replica.newNRTPoint(2, replica.getCurrentSearchingVersion());
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
      replica.newPrimaryThread.start();
      try {
        replica.newPrimaryThread.join(5000);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      for (String name : files.keySet()) {
        dest.dir.createOutput(name, IOContext.DEFAULT).close();
      }
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
      return files.keySet();
    }

    @Override
    public Set<String> getFileNames() {
      return files.keySet();
    }

    @Override
    public CopyState getCopyState() {
      return null;
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
