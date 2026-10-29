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
package org.apache.lucene.misc.index;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Version;

public class TestMultiPassIndexSplitterFailedSplit extends LuceneTestCase {

  public void testFailedSplitReleasesOutputLock() throws Exception {
    try (Directory input = new ByteBuffersDirectory();
        Directory out1 = new ByteBuffersDirectory();
        Directory out2 = new ByteBuffersDirectory()) {
      // an index created by the previous major version, which addIndexes into split()'s outputs rejects
      IndexWriterConfig iwc =
          new IndexWriterConfig(null).setIndexCreatedVersionMajor(Version.LATEST.major - 1);
      try (IndexWriter w = new IndexWriter(input, iwc)) {
        w.addDocument(new Document());
        w.addDocument(new Document());
      }
      try (DirectoryReader reader = DirectoryReader.open(input)) {
        expectThrows(
            IllegalArgumentException.class,
            () -> new MultiPassIndexSplitter().split(reader, new Directory[] {out1, out2}, false));
      }
      try {
        new IndexWriter(out1, new IndexWriterConfig(null)).close();
      } catch (LockObtainFailedException e) {
        fail("expected the failed split to release out1's write lock, but: " + e.getMessage());
      }
    }
  }
}
