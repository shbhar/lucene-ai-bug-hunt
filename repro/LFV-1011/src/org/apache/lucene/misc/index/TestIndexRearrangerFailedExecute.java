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

import com.carrotsearch.randomizedtesting.ThreadFilter;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakFilters;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.QuickPatchThreadsFilter;
import org.apache.lucene.util.FixedBitSet;

@ThreadLeakFilters(
    filters = {
      QuickPatchThreadsFilter.class,
      TestIndexRearrangerFailedExecute.RearrangerPool.class
    })
public class TestIndexRearrangerFailedExecute extends LuceneTestCase {

  public static class RearrangerPool implements ThreadFilter {
    @Override
    public boolean reject(Thread t) {
      return t.getName().startsWith("rearranger-");
    }
  }

  public void testFailedExecuteClosesReaderUnderRunningSelector() throws Exception {
    assumeTrue("needs two rearranger threads", Runtime.getRuntime().availableProcessors() >= 2);
    Directory in = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(in, new IndexWriterConfig())) {
      w.addDocument(new Document());
    }
    Thread caller = Thread.currentThread();
    CompletableFuture<Void> secondEntered = new CompletableFuture<>();
    CompletableFuture<Void> callerGotFailure = new CompletableFuture<>();
    CompletableFuture<Void> readerClosed = new CompletableFuture<>();
    CompletableFuture<Boolean> closedUnderSecond = new CompletableFuture<>();
    IndexRearranger.DocumentSelector first =
        reader -> {
          secondEntered.join();
          throw new IOException("first selector fails") {
            @Override
            public String toString() {
              callerGotFailure.complete(null);
              return super.toString();
            }
          };
        };
    IndexRearranger.DocumentSelector second =
        reader -> {
          reader.getReaderCacheHelper().addClosedListener(_ -> readerClosed.complete(null));
          secondEntered.complete(null);
          callerGotFailure.join();
          while (readerClosed.isDone() == false && caller.getState() == Thread.State.RUNNABLE) {
            Thread.onSpinWait();
          }
          closedUnderSecond.complete(readerClosed.isDone());
          return new FixedBitSet(reader.maxDoc());
        };
    IndexRearranger rearranger =
        new IndexRearranger(
            in, new ByteBuffersDirectory(), new IndexWriterConfig(), List.of(first, second));
    expectThrows(ExecutionException.class, rearranger::execute);
    // expected: the reader is closed after the second selector returns; actual: closed under it
    assertFalse("reader closed under the running second selector", closedUnderSecond.join());
  }
}
