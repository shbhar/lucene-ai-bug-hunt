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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestPersistentSnapshotSyncMetaData extends LuceneTestCase {

  public void testSnapshotSyncsSaveFileName() throws Exception {
    AtomicInteger syncMetaDataCalls = new AtomicInteger();
    try (Directory indexDir = new ByteBuffersDirectory();
        Directory snapshotDir =
            new FilterDirectory(new ByteBuffersDirectory()) {
              @Override
              public void syncMetaData() throws IOException {
                syncMetaDataCalls.incrementAndGet();
                super.syncMetaData();
              }
            }) {
      PersistentSnapshotDeletionPolicy psdp =
          new PersistentSnapshotDeletionPolicy(new KeepOnlyLastCommitDeletionPolicy(), snapshotDir);
      try (IndexWriter w =
          new IndexWriter(indexDir, new IndexWriterConfig().setIndexDeletionPolicy(psdp))) {
        w.addDocument(new Document());
        w.commit();
        psdp.snapshot();
        // expected: snapshot() makes the new snapshots_N name durable (syncMetaData); actual: never
        assertTrue(
            "expected >= 1 syncMetaData() call in snapshot(), got " + syncMetaDataCalls.get(),
            syncMetaDataCalls.get() >= 1);
      }
    }
  }
}
