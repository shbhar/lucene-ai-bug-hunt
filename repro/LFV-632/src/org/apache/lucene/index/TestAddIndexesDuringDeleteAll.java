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

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ThreadInterruptedException;

public class TestAddIndexesDuringDeleteAll extends LuceneTestCase {

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      throw new ThreadInterruptedException(e);
    }
  }

  public void testAddIndexesWhileDeleteAllAbortsMerge() throws Exception {
    CountDownLatch mergeRunning = new CountDownLatch(1);
    CountDownLatch mergeAborted = new CountDownLatch(1);
    CountDownLatch callerFlushed = new CountDownLatch(1);
    CountDownLatch callerDone = new CountDownLatch(1);
    ConcurrentMergeScheduler cms =
        new ConcurrentMergeScheduler() {
          @Override
          protected void doMerge(MergeSource source, MergePolicy.OneMerge merge)
              throws IOException {
            mergeRunning.countDown();
            try {
              merge
                  .getMergeProgress()
                  .pauseNanos(
                      Long.MAX_VALUE, MergePolicy.OneMergeProgress.PauseReason.OTHER, () -> true);
            } catch (InterruptedException e) {
              throw new ThreadInterruptedException(e);
            }
            mergeAborted.countDown();
            await(callerDone);
            super.doMerge(source, merge);
          }
        };
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setMergeScheduler(cms));
    w.addDocument(new Document());
    w.flush();
    w.addDocument(new Document());
    w.flush();
    w.forceMerge(1, false);
    await(mergeRunning);

    Directory src = newDirectory();
    try (IndexWriter sw = new IndexWriter(src, new IndexWriterConfig())) {
      sw.addDocument(new Document());
    }
    DirectoryReader srcReader = DirectoryReader.open(src);
    CodecReader parked =
        new FilterCodecReader((CodecReader) getOnlyLeafReader(srcReader)) {
          @Override
          public int numDocs() {
            callerFlushed.countDown();
            await(mergeAborted);
            return super.numDocs();
          }

          @Override
          public CacheHelper getCoreCacheHelper() {
            return null;
          }

          @Override
          public CacheHelper getReaderCacheHelper() {
            return null;
          }
        };
    AtomicReference<Throwable> thrown = new AtomicReference<>();
    Thread caller =
        new Thread(
            () -> {
              try {
                w.addIndexes(parked);
              } catch (Throwable t) {
                thrown.set(t);
              } finally {
                callerDone.countDown();
              }
            });
    caller.start();
    await(callerFlushed);
    w.deleteAll();
    caller.join();
    boolean open = w.isOpen();
    w.close();
    srcReader.close();
    dir.close();
    src.close();

    // expected: success or MergeAbortedException (deleteAll javadoc); actual: AlreadyClosedException on an open writer
    assertFalse(
        "writer isOpen()=" + open + ", but addIndexes threw " + thrown.get(),
        open && thrown.get() instanceof AlreadyClosedException);
  }
}
