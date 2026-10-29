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

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class TestSerialMergeSchedulerCommitDeadlock extends LuceneTestCase {

  public void testMergeFailureDuringMergeOnCommit() throws Exception {
    CountDownLatch merging = new CountDownLatch(1);
    CountDownLatch commitMerging = new CountDownLatch(1);
    Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexOutput createOutput(String name, IOContext context) throws IOException {
            if (context.context() == IOContext.Context.MERGE) {
              merging.countDown();
              await(commitMerging);
              throw new IOException("simulated disk full");
            }
            return super.createOutput(name, context);
          }
        };
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setMergeScheduler(new SerialMergeScheduler())
            .setIndexWriterEventListener(
                new IndexWriterEventListener() {
                  @Override
                  public void beginMergeOnFullFlush(MergePolicy.MergeSpecification merge) {
                    commitMerging.countDown();
                  }

                  @Override
                  public void endMergeOnFullFlush(MergePolicy.MergeSpecification merge) {}
                });
    IndexWriter w = new IndexWriter(dir, iwc);
    for (int i = 0; i < 3; i++) {
      w.addDocument(new Document());
      w.flush();
    }
    LogDocMergePolicy mp = new LogDocMergePolicy();
    mp.setMergeFactor(2);
    mp.setMinMergeDocs(2);
    w.getConfig().setMergePolicy(mp);

    start("merger", w::maybeMerge);
    await(merging);
    w.addDocument(new Document());
    Thread committer = start("committer", w::commit);
    committer.join(10_000);

    ThreadMXBean mx = ManagementFactory.getThreadMXBean();
    long[] ids = mx.findMonitorDeadlockedThreads();
    List<String> deadlocked =
        ids == null
            ? List.of()
            : Arrays.stream(mx.getThreadInfo(ids)).map(ThreadInfo::getThreadName).sorted().toList();
    // expected: commit() and the failed merge both return with an exception; actual: they deadlock
    assertEquals(List.of(), deadlocked);
  }

  private interface IOAction {
    void run() throws IOException;
  }

  private static Thread start(String name, IOAction action) {
    Thread t =
        new Thread(
            () -> {
              try {
                action.run();
              } catch (Exception _) {
              }
            },
            name);
    t.setDaemon(true);
    t.start();
    return t;
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue("timed out", latch.await(10, TimeUnit.SECONDS));
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }
}
