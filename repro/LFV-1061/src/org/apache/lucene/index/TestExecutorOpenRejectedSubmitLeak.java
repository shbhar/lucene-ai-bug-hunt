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

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestExecutorOpenRejectedSubmitLeak extends LuceneTestCase {

  public void testRejectedSubmitReleasesReaderOfAcceptedTask() throws Exception {
    Directory dir = newMockDirectory();
    IndexWriter w =
        new IndexWriter(dir, newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE));
    w.addDocument(new Document());
    w.commit();
    w.addDocument(new Document());
    w.close();

    AbstractExecutorService acceptOneThenReject =
        new AbstractExecutorService() {
          boolean accepted;

          @Override
          public void execute(Runnable task) {
            if (accepted) throw new RejectedExecutionException();
            accepted = true;
            task.run();
          }

          @Override
          public void shutdown() {}

          @Override
          public List<Runnable> shutdownNow() {
            return List.of();
          }

          @Override
          public boolean isShutdown() {
            return false;
          }

          @Override
          public boolean isTerminated() {
            return false;
          }

          @Override
          public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
          }
        };
    expectThrows(
        RejectedExecutionException.class, () -> DirectoryReader.open(dir, acceptOneThenReject));
    // expected: dir closes cleanly; actual: MockDirectoryWrapper reports the accepted segment's files still open
    dir.close();
  }
}
