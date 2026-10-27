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
package org.apache.lucene.search;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

/**
 * {@link ControlledRealTimeReopenThread#close()} sets the searching generation to Long.MAX_VALUE
 * "so any waiting search threads will return", but the refresh listener it registered on the
 * {@link ReferenceManager} is never removed. Any later refresh of the (still open) manager
 * overwrites Long.MAX_VALUE with the writer's generation, so a later {@code
 * waitForGeneration(gen)} on the closed instance blocks until somebody else happens to refresh the
 * manager again, although the reopen thread is gone.
 *
 * <p>Only public API is used. The timeout below only detects the blocked call; the trigger itself
 * is sequential.
 */
public class TestWaitForGenerationAfterClose extends LuceneTestCase {

  public void testWaitForGenerationReturnsAfterCloseAndLaterRefresh() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    SearcherManager mgr = new SearcherManager(w, null);
    ControlledRealTimeReopenThread<IndexSearcher> crt =
        new ControlledRealTimeReopenThread<>(w, mgr, 5.0, 0.0);
    crt.setDaemon(true);
    crt.start();
    Thread waiter = null;
    try {
      // normal operation
      long gen1 = w.addDocument(new Document());
      crt.waitForGeneration(gen1);

      crt.close();
      assertEquals(Long.MAX_VALUE, crt.getSearchingGen());

      // the application keeps using (and refreshing) its SearcherManager
      w.addDocument(new Document());
      mgr.maybeRefreshBlocking();
      long searchingGenAfterRefresh = crt.getSearchingGen();

      // a search thread that still holds the closed instance waits for its own change
      final long gen2 = w.addDocument(new Document());
      CountDownLatch returned = new CountDownLatch(1);
      AtomicReference<Throwable> error = new AtomicReference<>();
      waiter =
          new Thread(
              () -> {
                try {
                  crt.waitForGeneration(gen2);
                } catch (Throwable t) {
                  error.set(t);
                } finally {
                  returned.countDown();
                }
              });
      waiter.setDaemon(true);
      waiter.start();
      boolean didReturn = returned.await(10, TimeUnit.SECONDS);
      assertNull(error.get());
      // waitForGeneration javadoc: returns "if this call returned due to a close()"; close():
      // "Max it out so any waiting search threads will return"
      assertTrue(
          "waitForGeneration("
              + gen2
              + ") on a closed ControlledRealTimeReopenThread is still blocked after 10 s; "
              + "getSearchingGen() went from Long.MAX_VALUE back to "
              + searchingGenAfterRefresh
              + " when the manager was refreshed after close()",
          didReturn);
    } finally {
      // release a blocked waiter (another refresh covers gen2), then clean up
      mgr.maybeRefreshBlocking();
      if (waiter != null) {
        waiter.join(10_000);
      }
      mgr.close();
      w.close();
      dir.close();
    }
  }
}
