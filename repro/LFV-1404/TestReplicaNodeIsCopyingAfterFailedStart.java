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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestReplicaNodeIsCopyingAfterFailedStart extends LuceneTestCase {

  public void testIsCopyingFalseAfterCopyJobStartFails() throws Exception {
    try (ReplicaNode replica = new FailingStartReplica(newDirectory())) {
      replica.start(0);
      assertNull(replica.newNRTPoint(0, 1));
      // expected: isCopying() false once newNRTPoint gave up; actual: true (curNRTCopy not cleared)
      assertFalse(replica.isCopying());
    }
  }

  private static class FailingStartReplica extends ReplicaNode {
    FailingStartReplica(Directory dir) throws IOException {
      super(1, dir, null, new PrintStream(OutputStream.nullOutputStream(), true, UTF_8));
    }

    @Override
    protected CopyJob newCopyJob(
        String reason,
        Map<String, FileMetaData> files,
        Map<String, FileMetaData> prevFiles,
        boolean highPriority,
        CopyJob.OnceDone onceDone)
        throws IOException {
      Map<String, FileMetaData> nrtFiles =
          Map.of("_0.cfs", new FileMetaData(new byte[0], new byte[0], 100, 0));
      CopyState state = new CopyState(nrtFiles, 1, 1, new byte[0], Set.of(), 0, null);
      return new CopyJob(reason, nrtFiles, this, highPriority, onceDone) {
        @Override
        public void start() throws IOException {
          IOException broken = new IOException("connection to primary closed");
          cancel("exc during start", broken);
          throw new NodeCommunicationException("exc during start", broken);
        }

        @Override
        public boolean getFailed() {
          return exc != null;
        }

        @Override
        public CopyState getCopyState() {
          return state;
        }

        @Override
        public Set<String> getFileNames() {
          return files.keySet();
        }

        @Override
        public Set<String> getFileNamesToCopy() {
          return files.keySet();
        }

        @Override
        protected CopyOneFile newCopyOneFile(CopyOneFile current) {
          throw new UnsupportedOperationException();
        }

        @Override
        public void runBlocking() {
          throw new UnsupportedOperationException();
        }

        @Override
        public boolean conflicts(CopyJob other) {
          return false;
        }

        @Override
        public void finish() {
          throw new UnsupportedOperationException();
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
      throw new UnsupportedOperationException();
    }

    @Override
    protected void sendNewReplica() {}
  }
}
