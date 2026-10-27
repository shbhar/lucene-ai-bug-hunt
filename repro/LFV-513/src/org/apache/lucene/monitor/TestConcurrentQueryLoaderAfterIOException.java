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
package org.apache.lucene.monitor;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A ConcurrentQueryLoader worker leaves its loop at the first IOException from
 * Monitor.register (here: the query index cannot fsync its commit). Once every worker has
 * stopped, nothing drains the bounded queue, so ConcurrentQueryLoader.add blocks forever as soon
 * as the queue is full, and the caller never reaches close(), which is where the loader reports
 * the IOException. The usage pattern in the class javadoc therefore hangs instead of failing.
 */
public class TestConcurrentQueryLoaderAfterIOException extends LuceneTestCase {

  public void testAddDoesNotBlockForeverAfterAWorkerFailure() throws Exception {
    AtomicBoolean failSync = new AtomicBoolean(false);
    CountDownLatch failed = new CountDownLatch(1);
    Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public void sync(Collection<String> names) throws IOException {
            if (failSync.get()) {
              failed.countDown();
              throw new IOException("simulated fsync failure");
            }
            super.sync(names);
          }
        };
    Monitor monitor =
        new Monitor(
            new WhitespaceAnalyzer(), new MonitorConfiguration().setDirectoryProvider(() -> dir, null));
    ConcurrentQueryLoader loader = new ConcurrentQueryLoader(monitor, 1, 4);
    AtomicReference<Throwable> closeResult = new AtomicReference<>();
    Thread producer =
        new Thread(
            () -> {
              try {
                for (int i = 1; i <= 10; i++) {
                  loader.add(new MonitorQuery("q" + i, new TermQuery(new Term("f", "t" + i))));
                }
                loader.close();
                closeResult.set(new AssertionError("close() did not report the IOException"));
              } catch (Throwable t) {
                closeResult.set(t);
              }
            });
    try {
      failSync.set(true);
      loader.add(new MonitorQuery("q0", new TermQuery(new Term("f", "t0"))));
      assertTrue(failed.await(30, TimeUnit.SECONDS)); // the worker's register() has failed
      failSync.set(false);

      producer.start();
      producer.join(TimeUnit.SECONDS.toMillis(30));
      assertFalse(
          "ConcurrentQueryLoader.add() is still blocked 30 s after the loader's only worker stopped",
          producer.isAlive());
      // the expected outcome: the loader surfaces the IOException instead of hanging
      assertTrue(String.valueOf(closeResult.get()), closeResult.get() instanceof IOException);
    } finally {
      producer.interrupt(); // unblock add() so that the test can clean up
      producer.join();
      try {
        loader.close();
      } catch (IOException _) {
        // the recorded worker failure
      }
      monitor.close();
    }
  }
}
