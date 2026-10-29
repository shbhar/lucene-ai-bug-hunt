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
package org.apache.lucene.store;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestNRTCachingDirectoryCommitCrash extends LuceneTestCase {

  public void testCrashRightAfterSecondCommitRename() throws Exception {
    Directory crashState = new ByteBuffersDirectory();
    Directory delegate =
        new FilterDirectory(new ByteBuffersDirectory()) {
          String[] durableNames = new String[0];

          @Override
          public void syncMetaData() throws IOException {
            super.syncMetaData();
            durableNames = listAll();
          }

          @Override
          public void rename(String source, String dest) throws IOException {
            super.rename(source, dest);
            if (dest.equals("segments_2")) {
              for (String name : durableNames) {
                if (name.equals(source) == false) {
                  crashState.copyFrom(in, name, name, IOContext.DEFAULT);
                }
              }
              crashState.copyFrom(in, dest, dest, IOContext.DEFAULT);
            }
          }
        };
    try (IndexWriter w =
        new IndexWriter(new NRTCachingDirectory(delegate, 5.0, 60.0), new IndexWriterConfig())) {
      w.addDocument(new Document());
      w.commit();
      w.addDocument(new Document());
      w.commit();
    }
    // expected: crash state opens at commit 1 or 2; actual: _1 files were created after the fsync
    try {
      DirectoryReader.open(crashState).close();
    } catch (IOException e) {
      fail(
          "crash state (names durable at the last directory fsync, plus the segments_2 rename)"
              + " should open at commit 1 or 2, but: "
              + e);
    }
  }
}
