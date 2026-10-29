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

import java.time.Duration;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.ThreadInterruptedException;

public class TestWaitForGenerationAfterClose extends LuceneTestCase {

  public void testWaitForGenerationReturnsAfterCloseAndRefresh() throws Exception {
    ByteBuffersDirectory dir = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    SearcherManager mgr = new SearcherManager(w, null);
    ControlledRealTimeReopenThread<IndexSearcher> crt =
        new ControlledRealTimeReopenThread<>(w, mgr, Duration.ofSeconds(5), Duration.ZERO);
    crt.start();
    crt.close();
    mgr.maybeRefreshBlocking();
    long searchingGen = crt.getSearchingGen();
    long gen = w.addDocument(new Document());
    Thread waiter =
        new Thread(
            () -> {
              try {
                crt.waitForGeneration(gen);
              } catch (InterruptedException e) {
                throw new ThreadInterruptedException(e);
              }
            });
    waiter.start();
    waiter.join(5000);
    boolean blocked = waiter.isAlive();
    mgr.maybeRefreshBlocking();
    waiter.join();
    IOUtils.close(mgr, w, dir);
    // expected: a wait on the closed instance returns; actual: it blocks until a later refresh
    assertFalse(
        "waitForGeneration("
            + gen
            + ") on the closed ControlledRealTimeReopenThread still blocked after 5 s"
            + " (getSearchingGen()="
            + searchingGen
            + " after one refresh following close())",
        blocked);
  }
}
