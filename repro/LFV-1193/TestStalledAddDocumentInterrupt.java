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
import org.apache.lucene.util.ThreadInterruptedException;

public class TestStalledAddDocumentInterrupt extends LuceneTestCase {

  public void testInterruptedStalledAddDocumentTwice() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.getDocsWriter().flushControl.stallControl.updateStalled(true);
      for (int i = 0; i < 2; i++) {
        Thread.currentThread().interrupt();
        // expected both times: ThreadInterruptedException; with -ea the 2nd call fails an assert
        expectThrows(ThreadInterruptedException.class, () -> w.addDocument(new Document()));
      }
      w.getDocsWriter().flushControl.stallControl.updateStalled(false);
    }
  }
}
