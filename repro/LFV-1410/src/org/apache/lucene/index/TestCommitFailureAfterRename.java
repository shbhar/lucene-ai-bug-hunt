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

import org.apache.lucene.document.Document;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

public class TestCommitFailureAfterRename extends LuceneTestCase {

  public void testRenamedCommitStaysReadable() throws Exception {
    boolean[] armed = {false};
    InfoStream failAfterRename =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            if (armed[0] && message.startsWith("commit: done writing segments file")) {
              armed[0] = false;
              throw new RuntimeException("log failed");
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return armed[0];
          }

          @Override
          public void close() {}
        };
    try (Directory dir = FSDirectory.open(createTempDir())) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setInfoStream(failAfterRename));
      w.addDocument(new Document());
      w.commit();
      w.addDocument(new Document());
      w.prepareCommit();
      w.forceMerge(1);
      armed[0] = true;
      expectThrows(RuntimeException.class, w::commit);
      assertNotNull(w.getTragicException());
      // Expected: segments_2, renamed before the throw, opens with 2 docs; actual: its files were deleted, open throws.
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(2, r.numDocs());
      }
    }
  }
}
