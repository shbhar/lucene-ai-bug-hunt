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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestPrimaryRetryAfterFailedPublish extends LuceneTestCase {

  public void testRetryPublishesNewPoint() throws Exception {
    boolean[] failOnce = {false};
    StackWalker walker = StackWalker.getInstance();
    FilterDirectory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexInput openInput(String name, IOContext context) throws IOException {
            String method = "readLocalFileMetaData";
            if (failOnce[0] && walker.walk(s -> s.anyMatch(f -> f.getMethodName().equals(method)))) {
              failOnce[0] = false;
              throw new IOException("transient I/O error opening " + name);
            }
            return super.openInput(name, context);
          }
        };
    IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig());
    PrimaryNode primary =
        new PrimaryNode(writer, 0, 0, -1, null, null) {
          @Override
          protected void preCopyMergedSegmentFiles(
              SegmentCommitInfo info, Map<String, FileMetaData> files) {}
        };
    try {
      writer.addDocument(new Document());
      failOnce[0] = true;
      expectThrows(IOException.class, primary::flushAndRefresh);
      primary.flushAndRefresh();
      // expected: the retry publishes the new point to replicas; actual: copy state stays old
      assertEquals(primary.getCurrentSearchingVersion(), primary.getCopyStateVersion());
    } finally {
      primary.close();
    }
  }
}
