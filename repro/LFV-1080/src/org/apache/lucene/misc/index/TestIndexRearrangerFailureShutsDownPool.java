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
package org.apache.lucene.misc.index;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class TestIndexRearrangerFailureShutsDownPool extends LuceneTestCase {

  public void testFailedExecuteStopsWorker() throws Exception {
    try (Directory in = newDirectory();
        Directory out = newDirectory()) {
      try (IndexWriter w = new IndexWriter(in, newIndexWriterConfig())) {
        w.addDocument(new Document());
      }
      AtomicReference<Thread> worker = new AtomicReference<>();
      IndexRearranger.DocumentSelector failing =
          reader -> {
            worker.set(Thread.currentThread());
            throw new IOException("selector failed");
          };
      IndexRearranger rearranger =
          new IndexRearranger(in, out, newIndexWriterConfig(), List.of(failing));
      expectThrows(ExecutionException.class, rearranger::execute);
      // expected: the pool's worker thread ends after execute() fails; actual: it stays alive forever
      worker.get().join(10_000);
      assertFalse(worker.get().getName() + " still alive", worker.get().isAlive());
    }
  }
}
