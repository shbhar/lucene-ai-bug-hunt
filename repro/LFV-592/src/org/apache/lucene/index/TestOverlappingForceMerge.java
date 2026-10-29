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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ThreadInterruptedException;

public class TestOverlappingForceMerge extends LuceneTestCase {

  public void testForceMergeOneOverlappedByForceMergeTwo() throws Exception {
    Thread mainThread = Thread.currentThread();
    CountDownLatch firstCallPlanned = new CountDownLatch(1);
    CountDownLatch secondCallPlanned = new CountDownLatch(1);
    LogDocMergePolicy mp =
        new LogDocMergePolicy() {
          @Override
          public MergeSpecification findMerges(
              MergeTrigger trigger, SegmentInfos infos, MergeContext context) {
            return null;
          }

          @Override
          public MergeSpecification findForcedMerges(
              SegmentInfos infos,
              int maxSegmentCount,
              Map<SegmentCommitInfo, Boolean> segmentsToMerge,
              MergeContext context)
              throws IOException {
            (Thread.currentThread() == mainThread ? firstCallPlanned : secondCallPlanned)
                .countDown();
            return super.findForcedMerges(infos, maxSegmentCount, segmentsToMerge, context);
          }
        };
    mp.setMergeFactor(2);
    ConcurrentMergeScheduler cms =
        new ConcurrentMergeScheduler() {
          @Override
          protected void doMerge(MergeSource source, MergePolicy.OneMerge merge)
              throws IOException {
            try {
              secondCallPlanned.await();
            } catch (InterruptedException e) {
              throw new ThreadInterruptedException(e);
            }
            super.doMerge(source, merge);
          }
        };
    Directory dir = newDirectory();
    IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(mp).setMergeScheduler(cms));
    for (int i = 0; i < 3; i++) {
      w.addDocument(new Document());
      w.flush();
    }
    Thread second =
        new Thread(
            () -> {
              try {
                firstCallPlanned.await();
                w.forceMerge(2);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
    second.start();
    w.forceMerge(1);
    int segments;
    try (DirectoryReader r = DirectoryReader.open(w)) {
      segments = r.leaves().size();
    }
    second.join();
    w.close();
    dir.close();

    // forceMerge(maxNumSegments): "maximum number of segments left in the index after merging finishes"
    assertEquals("segments when forceMerge(1) returned", 1, segments);
  }
}
