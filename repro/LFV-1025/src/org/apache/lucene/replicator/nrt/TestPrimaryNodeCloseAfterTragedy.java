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
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestPrimaryNodeCloseAfterTragedy extends LuceneTestCase {

  public void testCloseAfterReleaseOnTragicWriter() throws Exception {
    boolean[] diskFull = {false};
    FilterDirectory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexOutput createOutput(String name, IOContext context) throws IOException {
            if (diskFull[0]) {
              throw new IOException("disk full");
            }
            return super.createOutput(name, context);
          }
        };
    IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig());
    PrimaryNode primary =
        new PrimaryNode(writer, 0, 1, -1, null, null) {
          @Override
          protected void preCopyMergedSegmentFiles(
              SegmentCommitInfo info, Map<String, FileMetaData> files) {}
        };
    CopyState copyState = primary.getCopyState();
    diskFull[0] = true;
    expectThrows(IOException.class, () -> writer.addDocument(new Document()));
    expectThrows(AlreadyClosedException.class, () -> primary.releaseCopyState(copyState));

    Thread closer =
        new Thread(
            () -> {
              try {
                primary.close();
              } catch (Exception _) {
              }
            });
    closer.start();
    closer.join(10_000);
    try {
      // expected: close() returns or throws once the only copy was released; actual: it waits forever
      assertFalse("PrimaryNode.close() still waits for a released copy", closer.isAlive());
    } finally {
      closer.interrupt();
      closer.join();
    }
  }
}
