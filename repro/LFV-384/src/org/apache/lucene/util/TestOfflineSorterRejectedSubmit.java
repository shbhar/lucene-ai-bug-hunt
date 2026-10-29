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
package org.apache.lucene.util;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestOfflineSorterRejectedSubmit extends LuceneTestCase {

  public void testSortAfterRejectedSubmit() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexOutput out = dir.createOutput("input", IOContext.DEFAULT)) {
        new OfflineSorter.ByteSequencesWriter(out).write(new byte[] {42});
        CodecUtil.writeFooter(out);
      }
      ExecutorService exec = Executors.newSingleThreadExecutor(new NamedThreadFactory("sorter"));
      exec.shutdown();
      OfflineSorter sorter =
          new OfflineSorter(
              dir,
              "tmp",
              OfflineSorter.DEFAULT_COMPARATOR,
              OfflineSorter.BufferSize.megabytes(1),
              OfflineSorter.MAX_TEMPFILES,
              -1,
              exec,
              1);
      expectThrows(RejectedExecutionException.class, () -> sorter.sort("input"));

      FutureTask<String> secondSort = new FutureTask<>(() -> sorter.sort("input"));
      Thread t = new Thread(secondSort);
      t.start();
      t.join(10_000);
      boolean blocked = t.isAlive();
      t.interrupt();
      t.join();
      // expected: the second sort() is rejected again; actual: it waits for the permit the first one lost
      assertFalse("second sort() still blocked after 10 s, expected RejectedExecutionException", blocked);
      assertTrue(
          expectThrows(ExecutionException.class, secondSort::get).getCause()
              instanceof RejectedExecutionException);
    }
  }
}
