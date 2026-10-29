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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ThreadInterruptedException;

public class TestCheckIndexInterruptedCaller extends LuceneTestCase {

  public void testInterruptedCallerWithBusyExecutor() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(new Document());
    }
    ExecutorService executor = Executors.newSingleThreadExecutor();
    CountDownLatch busy = new CountDownLatch(1);
    executor.submit(
        () -> {
          busy.await();
          return null;
        });
    try (CheckIndex checker = new CheckIndex(dir)) {
      Thread.currentThread().interrupt();
      checker.checkIndex(null, executor);
    } catch (NullPointerException e) {
      throw new AssertionError("expected a Status or ThreadInterruptedException, got: " + e, e);
    } catch (ThreadInterruptedException expected) {
    } finally {
      Thread.interrupted();
      busy.countDown();
      executor.shutdown();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
  }
}
