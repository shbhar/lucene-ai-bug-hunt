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

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCMSLiveResetToAutoDetect extends LuceneTestCase {

  public void testResetToAutoDetectOnOpenWriter() throws Exception {
    ConcurrentMergeScheduler cms = new ConcurrentMergeScheduler();
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setMergeScheduler(cms))) {
      cms.setMaxMergesAndThreads(
          ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS,
          ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS);
      w.addDocument(new Document());
      w.flush();
      w.addDocument(new Document());
      FutureTask<Void> forceMerge =
          new FutureTask<>(
              () -> {
                w.forceMerge(1);
                return null;
              });
      Thread t = new Thread(forceMerge);
      t.start();
      try {
        // expected: forceMerge(1) returns; actual: it waits in maybeStall forever (limits stay -1)
        forceMerge.get(10, TimeUnit.SECONDS);
      } catch (TimeoutException _) {
        fail("forceMerge(1) hangs after resetting a live ConcurrentMergeScheduler to AUTO_DETECT");
      } finally {
        cms.setMaxMergesAndThreads(6, 1);
        t.join();
      }
    }
  }
}
