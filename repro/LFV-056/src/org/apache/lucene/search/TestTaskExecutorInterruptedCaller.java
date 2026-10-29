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

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTaskExecutorInterruptedCaller extends LuceneTestCase {

  public void testInvokeAllWaitsForRunningTaskWhenCallerIsInterrupted() throws Exception {
    Semaphore started = new Semaphore(0);
    CountDownLatch release = new CountDownLatch(1);
    AtomicBoolean task0Done = new AtomicBoolean();
    Executor executor = r -> { new Thread(r).start(); started.acquireUninterruptibly(); };
    Callable<Void> task0 = () -> {
      started.release();
      release.await(10, TimeUnit.SECONDS);
      task0Done.set(true);
      return null;
    };
    Callable<Void> task1 = () -> { Thread.currentThread().interrupt(); return null; };
    Throwable thrown = null;
    try {
      new TaskExecutor(executor).invokeAll(List.of(task0, task1));
    } catch (Throwable t) {
      thrown = t;
    }
    boolean task0DoneAtReturn = task0Done.get();
    release.countDown();
    // expected: invokeAll ends only after task 0 has completed; actual: it ends while task 0 still runs
    assertTrue("expected invokeAll to wait for task 0, but it ended with " + thrown + " while task 0 was still running", task0DoneAtReturn);
  }
}
