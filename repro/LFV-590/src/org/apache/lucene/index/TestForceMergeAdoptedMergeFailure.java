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
import java.util.concurrent.CountDownLatch;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;
import org.apache.lucene.util.ThreadInterruptedException;

public class TestForceMergeAdoptedMergeFailure extends LuceneTestCase {

  public void testFailedMergeStartedByForceMergeFlushIsReported() throws Exception {
    CountDownLatch forceMergeWaiting = new CountDownLatch(1);
    CountDownLatch forceMergeReturned = new CountDownLatch(1);
    FilterDirectory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexOutput createOutput(String name, IOContext context) throws IOException {
            if (context.context() == IOContext.Context.MERGE) {
              await(forceMergeWaiting);
              throw new IOException("disk full");
            }
            return super.createOutput(name, context);
          }
        };
    InfoStream infoStream =
        new InfoStream() {
          boolean mergeFinished;

          @Override
          public void message(String component, String message) {
            if (message.equals("forceMergeBeforeWait")) {
              forceMergeWaiting.countDown();
            } else if (message.equals("hit exception during merge")) {
              mergeFinished = true;
            } else if (message.startsWith("hit tragic") && mergeFinished) {
              await(forceMergeReturned);
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return true;
          }

          @Override
          public void close() {}
        };
    ConcurrentMergeScheduler cms =
        new ConcurrentMergeScheduler() {
          @Override
          protected void handleMergeException(Throwable exc) {}
        };
    LogDocMergePolicy mp = new LogDocMergePolicy();
    mp.setMergeFactor(2);
    IndexWriterConfig iwc =
        new IndexWriterConfig().setMergePolicy(mp).setMergeScheduler(cms).setInfoStream(infoStream);
    IndexWriter w =
        new IndexWriter(dir, iwc) {
          @Override
          protected boolean isEnableTestPoints() {
            return true;
          }
        };
    Document doc = new Document();
    doc.add(new StringField("id", "0", Field.Store.NO));
    w.addDocument(doc);
    w.commit();
    w.addDocument(doc);
    Exception thrown = null;
    try {
      w.forceMerge(1);
    } catch (IOException | IllegalStateException e) {
      thrown = e;
    } finally {
      forceMergeReturned.countDown();
      cms.sync();
    }
    assertNotNull(
        "expected forceMerge(1) to throw because the merge of _0 and _1 it waited for failed with "
            + w.getTragicException()
            + ", but it returned normally",
        thrown);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      throw new ThreadInterruptedException(e);
    }
  }
}
