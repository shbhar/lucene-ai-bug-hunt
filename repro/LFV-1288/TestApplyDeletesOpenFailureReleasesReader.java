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
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

public class TestApplyDeletesOpenFailureReleasesReader extends LuceneTestCase {

  public void testFailedApplyReleasesPooledReader() throws Exception {
    AtomicReference<Thread> helper = new AtomicReference<>();
    CountDownLatch helperAtApply = new CountDownLatch(1);
    CountDownLatch releaseHelper = new CountDownLatch(1);
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setReaderPooling(false)
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setInfoStream(
                new InfoStream() {
                  @Override
                  public void message(String component, String message) {
                    try {
                      if (Thread.currentThread() == helper.get()
                          && message.startsWith("now apply all deletes")) {
                        helperAtApply.countDown();
                        releaseHelper.await();
                      } else if (helperAtApply.getCount() == 0
                          && message.contains("finishFullFlush")) {
                        releaseHelper.countDown();
                        helper.get().join();
                      }
                    } catch (InterruptedException e) {
                      throw new RuntimeException(e);
                    }
                  }

                  @Override
                  public boolean isEnabled(String component) {
                    return "IW".equals(component) || "DW".equals(component);
                  }

                  @Override
                  public void close() {}
                });
    MockDirectoryWrapper dir = newMockDirectory();
    dir.failOn(
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper d) throws IOException {
            if (Thread.currentThread() == helper.get()
                && callStackContains(BufferedUpdatesStream.SegmentState.class, "<init>")) {
              throw new IOException("injected");
            }
          }
        });
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (String id : new String[] {"1", "2"}) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.NO));
        w.addDocument(doc);
      }
      w.commit();
      AtomicReference<Throwable> helperFailure = new AtomicReference<>();
      helper.set(
          new Thread(
              () -> {
                try {
                  w.flush();
                } catch (Throwable t) {
                  helperFailure.set(t);
                }
              }));
      helper.get().start();
      helperAtApply.await();
      w.deleteDocuments(new Term("id", "1"));
      w.flush();
      assertEquals("injected", helperFailure.get().getMessage());
      w.commit();
      // with pooling off the segment should leave the pool; actual: still pooled, refCount 2
      assertNull(w.getPooledInstance(w.newestSegment(), false));
    }
    dir.close();
  }
}
