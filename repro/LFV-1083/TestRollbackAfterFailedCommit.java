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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

public class TestRollbackAfterFailedCommit extends LuceneTestCase {

  // Expected: rollback() after a failed commit keeps the last commit (1 doc); actual: it deletes
  // that commit's files.
  public void testRollbackKeepsLastCommit() throws Exception {
    boolean[] fail = {false};
    InfoStream infoStream =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            if (fail[0] && message.startsWith("done all syncs")) {
              throw new RuntimeException("boom");
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return true;
          }

          @Override
          public void close() {}
        };
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setInfoStream(infoStream));
      w.addDocument(new Document());
      w.commit();
      w.addDocument(new Document());
      fail[0] = true;
      expectThrows(RuntimeException.class, w::commit);
      w.rollback();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.numDocs());
      }
    }
  }
}
