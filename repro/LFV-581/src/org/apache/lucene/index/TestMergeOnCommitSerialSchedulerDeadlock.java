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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Merge-on-commit (maxFullFlushMergeWaitMillis > 0) with SerialMergeScheduler: commit() runs the
 * commit merges through mergeScheduler.merge while it holds IndexWriter's commit lock, and
 * SerialMergeScheduler.merge is synchronized. If another thread is inside SerialMergeScheduler
 * running a merge that fails, that thread closes the writer (tragic event -> rollbackInternal),
 * which needs the commit lock. The two threads then wait for each other forever: commit() never
 * returns and the indexing thread never returns.
 */
@ThreadLeakScope(ThreadLeakScope.Scope.NONE) // the deadlocked threads cannot be stopped
public class TestMergeOnCommitSerialSchedulerDeadlock extends LuceneTestCase {

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  public void testFailingMergeDuringMergeOnCommit() throws Exception {
    CountDownLatch indexingThreadInMerge = new CountDownLatch(1);
    CountDownLatch commitHoldsCommitLock = new CountDownLatch(1);
    AtomicBoolean armed = new AtomicBoolean(false);

    MergePolicy mp =
        new FilterMergePolicy(NoMergePolicy.INSTANCE) {
          @Override
          public MergeSpecification findMerges(
              MergeTrigger trigger, SegmentInfos infos, MergeContext ctx) {
            // one merge, found by the indexing thread after its segment flush; it fails
            if (armed.get() == false || trigger != MergeTrigger.SEGMENT_FLUSH || infos.size() < 2) {
              return null;
            }
            armed.set(false);
            MergeSpecification spec = new MergeSpecification();
            spec.add(
                new OneMerge(List.of(infos.info(0), infos.info(1))) {
                  @Override
                  public CodecReader wrapForMerge(CodecReader reader) throws IOException {
                    indexingThreadInMerge.countDown();
                    try {
                      commitHoldsCommitLock.await();
                    } catch (InterruptedException e) {
                      throw new RuntimeException(e);
                    }
                    throw new IOException("simulated I/O error during merge (for example disk full)");
                  }
                });
            return spec;
          }

          @Override
          public MergeSpecification findFullFlushMerges(
              MergeTrigger trigger, SegmentInfos infos, MergeContext ctx) {
            // called by commit() while it holds the commit lock: let the failing merge proceed and
            // ask for an ordinary merge of the segments that are not being merged
            if (trigger != MergeTrigger.COMMIT || indexingThreadInMerge.getCount() != 0) {
              return null;
            }
            List<SegmentCommitInfo> free = new ArrayList<>();
            for (SegmentCommitInfo sci : infos) {
              if (ctx.getMergingSegments().contains(sci) == false) {
                free.add(sci);
              }
            }
            commitHoldsCommitLock.countDown();
            if (free.size() < 2) {
              return null;
            }
            MergeSpecification spec = new MergeSpecification();
            spec.add(new OneMerge(free));
            return spec;
          }
        };

    Directory dir = new ByteBuffersDirectory();
    IndexWriter w =
        new IndexWriter(
            dir,
            new IndexWriterConfig(new MockAnalyzer(random()))
                .setMergePolicy(mp)
                .setMergeScheduler(new SerialMergeScheduler())
                .setMaxFullFlushMergeWaitMillis(10_000)
                .setMaxBufferedDocs(2)
                .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH));
    for (int i = 0; i < 4; i++) {
      w.addDocument(doc("a" + i));
      w.flush(); // four one-document segments
    }
    armed.set(true);

    Thread indexer =
        new Thread(
            () -> {
              try {
                w.addDocument(doc("b0"));
                w.addDocument(doc("b1")); // flushes a segment, then runs the failing merge
              } catch (Throwable expected) {
                // the merge failure closes the writer
              }
            },
            "indexer");
    indexer.setDaemon(true);
    indexer.start();
    assertTrue(indexingThreadInMerge.await(30, TimeUnit.SECONDS));

    Thread committer =
        new Thread(
            () -> {
              try {
                w.commit();
              } catch (Throwable expected) {
                // AlreadyClosedException or the tragic exception would be fine
              }
            },
            "committer");
    committer.setDaemon(true);
    committer.start();

    committer.join(20_000);
    indexer.join(1_000);
    long[] deadlocked = ManagementFactory.getThreadMXBean().findMonitorDeadlockedThreads();
    boolean bothInCycle = false;
    if (deadlocked != null) {
      boolean c = false, i = false;
      for (long id : deadlocked) {
        c |= id == committer.threadId();
        i |= id == indexer.threadId();
      }
      bothInCycle = c && i;
    }
    assertFalse(
        "commit() and the indexing thread are in a JVM-detected monitor deadlock with each other"
            + " (writer tragic exception="
            + w.getTragicException()
            + ")",
        bothInCycle);
    assertFalse(
        "commit() and the indexing thread are deadlocked (committer alive="
            + committer.isAlive()
            + ", indexer alive="
            + indexer.isAlive()
            + ", JVM-detected monitor deadlock="
            + (deadlocked != null)
            + ", writer tragic exception="
            + w.getTragicException()
            + ")",
        committer.isAlive() || indexer.isAlive());
  }
}
