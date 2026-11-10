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
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestReplicaNodeCloseAfterFailedStart extends LuceneTestCase {

  public void testCloseAfterFailedStartReleasesWriteLock() throws Exception {
    Path path = createTempDir();
    PrintStream log =
        new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
    ReplicaNode replica =
        new ReplicaNode(0, FSDirectory.open(path), null, log) {
          @Override
          protected CopyJob newCopyJob(
              String reason,
              Map<String, FileMetaData> files,
              Map<String, FileMetaData> prevFiles,
              boolean highPriority,
              CopyJob.OnceDone onceDone) {
            throw new UnsupportedOperationException();
          }

          @Override
          protected void launch(CopyJob job) {
            throw new UnsupportedOperationException();
          }

          @Override
          protected void sendNewReplica() throws IOException {
            throw new IOException("primary unreachable");
          }
        };
    expectThrows(IOException.class, () -> replica.start(0));

    // Expected: close() releases the write lock. Actual: NullPointerException, lock stays held.
    replica.close();
    try (Directory dir = FSDirectory.open(path)) {
      new IndexWriter(dir, new IndexWriterConfig()).close();
    }
  }
}
