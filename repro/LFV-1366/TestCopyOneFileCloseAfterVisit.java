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

import java.util.Map;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.StringHelper;

public class TestCopyOneFileCloseAfterVisit extends LuceneTestCase {

  public void testCloseAfterCompletedVisit() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    try (IndexOutput out = dir.createOutput("f", IOContext.DEFAULT)) {
      CodecUtil.writeIndexHeader(out, "test", 0, StringHelper.randomId(), "");
      CodecUtil.writeFooter(out);
    }
    ReplicaNode replica =
        new ReplicaNode(0, dir, null, null) {
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
          protected void sendNewReplica() {}
        };
    CopyOneFile copy =
        new CopyOneFile(
            dir.openInput("f", IOContext.DEFAULT),
            replica,
            "f",
            replica.readLocalFileMetaData("f"),
            new byte[1024]);
    assertTrue(copy.visit());
    copy.close(); // expected: no effect (Closeable); actual: IllegalStateException
  }
}
