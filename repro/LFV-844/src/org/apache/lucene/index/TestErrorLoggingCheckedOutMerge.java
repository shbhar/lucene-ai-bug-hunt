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
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

public class TestErrorLoggingCheckedOutMerge extends LuceneTestCase {

  public void testWriterClosesItselfAfterError() throws Exception {
    InfoStream infoStream =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            if (message.startsWith("  checked out merge")) {
              throw new OutOfMemoryError();
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return "MS".equals(component);
          }

          @Override
          public void close() {}
        };
    LogDocMergePolicy mergePolicy = new LogDocMergePolicy();
    mergePolicy.setMergeFactor(2);
    IndexWriter w =
        new IndexWriter(
            new ByteBuffersDirectory(),
            new IndexWriterConfig()
                .setMaxBufferedDocs(2)
                .setMergePolicy(mergePolicy)
                .setInfoStream(infoStream));
    for (int i = 0; i < 3; i++) {
      w.addDocument(new Document());
    }
    // the 4th document flushes a 2nd segment, so LogDocMergePolicy selects a merge to check out
    Thread indexer =
        new Thread(() -> expectThrows(OutOfMemoryError.class, () -> w.addDocument(new Document())));
    indexer.start();
    indexer.join(10_000);
    assertFalse(
        "expected addDocument to throw the Error, still blocked after 10 s", indexer.isAlive());
    assertFalse("expected the writer to have closed itself after the Error", w.isOpen());
  }
}
