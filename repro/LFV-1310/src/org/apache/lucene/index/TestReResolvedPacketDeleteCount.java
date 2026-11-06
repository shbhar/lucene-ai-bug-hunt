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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

public class TestReResolvedPacketDeleteCount extends LuceneTestCase {

  public void testReResolvedPacketReportsOnlyNewDeletes() throws Exception {
    CountDownLatch mergeStarted = new CountDownLatch(1);
    CountDownLatch releaseMerge = new CountDownLatch(1);
    CountDownLatch mergeFinished = new CountDownLatch(1);
    Thread testThread = Thread.currentThread();
    List<String> bd = new CopyOnWriteArrayList<>();
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setMergePolicy(
        new FilterMergePolicy(NoMergePolicy.INSTANCE) {
          @Override
          public MergeSpecification findMerges(
              MergeTrigger trigger, SegmentInfos infos, MergeContext ctx) {
            if (trigger != MergeTrigger.EXPLICIT) {
              return null;
            }
            MergeSpecification spec = new MergeSpecification();
            spec.add(
                new OneMerge(infos.asList()) {
                  @Override
                  public CodecReader wrapForMerge(CodecReader reader) throws java.io.IOException {
                    mergeStarted.countDown();
                    waitFor(releaseMerge);
                    return reader;
                  }

                  @Override
                  public void mergeFinished(boolean success, boolean segmentDropped) {
                    mergeFinished.countDown();
                  }
                });
            return spec;
          }
        });
    iwc.setInfoStream(
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            if (Thread.currentThread() != testThread) {
              return;
            }
            bd.add(message);
            if (message.startsWith("applyTermDeletes took") && releaseMerge.getCount() > 0) {
              releaseMerge.countDown();
              waitFor(mergeFinished);
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return "BD".equals(component);
          }

          @Override
          public void close() {}
        });
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocuments(List.of(doc("a"), doc("a"), doc("a"), doc("b")));
      w.flush();
      w.addDocuments(List.of(doc("a"), doc("b")));
      w.flush();
      w.maybeMerge();
      waitFor(mergeStarted);
      w.deleteDocuments(new Term("key", "a"));
      w.flush();
    }
    String done = bd.stream().filter(m -> m.startsWith("done apply del packet")).findFirst().get();
    // Expected: the packet reports its 4 deleted documents; actual: 8 after the second pass.
    assertTrue(done, done.contains("; 4 new deletes/updates;"));
  }

  private static void waitFor(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private static Document doc(String key) {
    Document d = new Document();
    d.add(new StringField("key", key, Field.Store.NO));
    return d;
  }
}
