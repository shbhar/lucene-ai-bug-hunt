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

public class TestFailedSegmentPublishDropsDocument extends LuceneTestCase {

  public void testCommitAfterFailedPublishKeepsDocument() throws Exception {
    AtomicBoolean failOnce = new AtomicBoolean();
    InfoStream infoStream =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            if (message.startsWith("publishFlushedSegment") && failOnce.getAndSet(false)) {
              throw new IllegalStateException("logging failed");
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return true;
          }

          @Override
          public void close() {}
        };
    Directory dir = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setInfoStream(infoStream));
    w.addDocument(new Document());
    failOnce.set(true);
    expectThrows(IllegalStateException.class, w::commit);
    // expected: the writer closes itself (tragic) or the next commit keeps the document;
    // actual: it stays open and commits 0 docs
    if (w.isOpen()) {
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.numDocs());
      }
    }
    w.close();
    dir.close();
  }
}
