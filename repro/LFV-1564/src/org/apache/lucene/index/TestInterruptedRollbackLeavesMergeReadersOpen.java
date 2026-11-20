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

import java.util.concurrent.CountDownLatch;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;
import org.apache.lucene.util.ThreadInterruptedException;

public class TestInterruptedRollbackLeavesMergeReadersOpen extends LuceneTestCase {

  public void testInterruptedRollbackWhileAnotherThreadMerges() throws Exception {
    CountDownLatch mergeRunning = new CountDownLatch(1);
    CountDownLatch rolledBack = new CountDownLatch(1);
    InfoStream holdMerge =
        new InfoStream() {
          @Override
          public boolean isEnabled(String component) {
            return component.equals("SM");
          }

          @Override
          public void message(String component, String message) {
            mergeRunning.countDown();
            try {
              rolledBack.await();
            } catch (InterruptedException e) {
              throw new AssertionError(e);
            }
          }

          @Override
          public void close() {}
        };
    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    IndexWriter writer =
        new IndexWriter(
            dir,
            new IndexWriterConfig()
                .setMergeScheduler(new SerialMergeScheduler())
                .setInfoStream(holdMerge));
    writer.addDocument(new Document());
    writer.flush();
    writer.addDocument(new Document());
    writer.flush();
    Thread merger =
        new Thread(() -> expectThrows(AlreadyClosedException.class, () -> writer.forceMerge(1)));
    merger.start();
    mergeRunning.await();

    Thread.currentThread().interrupt();
    expectThrows(ThreadInterruptedException.class, writer::rollback);
    rolledBack.countDown();
    merger.join();
    // expected: no file is left open; actual: "cannot close: there are still 2 open files"
    dir.close();
  }
}
