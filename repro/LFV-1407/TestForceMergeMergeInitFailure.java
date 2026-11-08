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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

public class TestForceMergeMergeInitFailure extends LuceneTestCase {

  public void testForceMergeReportsMergeInitFailure() throws Exception {
    MockDirectoryWrapper dir = newMockDirectory();
    dir.setAssertNoUnrefencedFilesOnClose(false);
    AtomicBoolean diskFull = new AtomicBoolean();
    dir.failOn(
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper d) throws IOException {
            if (diskFull.get()
                && callStackContains(ReaderPool.class, "writeDocValuesUpdatesForMerge")) {
              diskFull.set(false);
              throw new IOException("simulated disk full");
            }
          }
        });
    CountDownLatch forceMergeReturned = new CountDownLatch(1);
    InfoStream holdBeforeRecordingFailure =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            if (message.startsWith("handleMergeException")) {
              try {
                forceMergeReturned.await(5, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                throw new AssertionError(e);
              }
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return "IW".equals(component);
          }

          @Override
          public void close() {}
        };
    AtomicReference<IndexWriter> writer = new AtomicReference<>();
    ConcurrentMergeScheduler cms =
        new ConcurrentMergeScheduler() {
          @Override
          protected void doMerge(MergeSource mergeSource, MergePolicy.OneMerge merge)
              throws IOException {
            writer.get().addDocument(new Document());
            writer.get().updateNumericDocValue(new Term("id", "0"), "v", 42);
            writer.get().flushNextBuffer();
            diskFull.set(true);
            super.doMerge(mergeSource, merge);
          }

          @Override
          protected void handleMergeException(Throwable exc) {}
        };
    IndexWriter w =
        new IndexWriter(
            dir,
            new IndexWriterConfig()
                .setMergeScheduler(cms)
                .setInfoStream(holdBeforeRecordingFailure));
    writer.set(w);
    for (int i = 0; i < 2; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), StringField.Store.NO));
      doc.add(new NumericDocValuesField("v", i));
      w.addDocument(doc);
      w.commit();
    }
    Exception thrown = null;
    try {
      w.forceMerge(1);
    } catch (IOException | IllegalStateException e) {
      thrown = e;
    } finally {
      forceMergeReturned.countDown();
    }
    cms.sync();
    assertFalse("the disk-full must hit the merge's mergeInit", diskFull.get());
    // expected: forceMerge throws because its merge failed; actual: it returns normally
    assertNotNull("forceMerge(1) returned normally; tragedy: " + w.getTragicException(), thrown);
    w.close();
    dir.close();
  }
}
