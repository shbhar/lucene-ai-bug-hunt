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

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

public class TestCommitLogFailureStopsFlushing extends LuceneTestCase {

  public void testFlushesAfterCommitFailedOnLogMessage() throws Exception {
    AtomicBoolean failNext = new AtomicBoolean();
    InfoStream infoStream =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            if (message.contains("finishFullFlush") && failNext.getAndSet(false)) {
              throw new IllegalStateException("log failure");
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return true;
          }

          @Override
          public void close() {}
        };
    IndexWriterConfig iwc = new IndexWriterConfig().setMaxBufferedDocs(2).setInfoStream(infoStream);
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, iwc);
      try {
        w.addDocument(new Document());
        failNext.set(true);
        expectThrows(IllegalStateException.class, w::commit);
        assertNull(w.getTragicException());
        for (int i = 0; i < 10; i++) {
          w.addDocument(new Document());
        }
        // expected: the open writer still flushes every 2 docs (numRamDocs < 2); actual: 10
        assertTrue("numRamDocs=" + w.numRamDocs(), w.numRamDocs() < 2);
      } finally {
        w.rollback();
      }
    }
  }
}
