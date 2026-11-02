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
import java.io.UncheckedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDocValuesUpdateBufferHardLimitDuringFullFlush extends LuceneTestCase {

  // Expected: DV updates stall at the 256 KB delete-buffer limit during a full flush; actual: ~2 MB
  public void testDocValuesUpdateBufferHardLimitDuringFullFlush() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicBoolean hold = new AtomicBoolean();
    try (Directory dir =
        new FilterDirectory(newDirectory()) {
          @Override
          public IndexOutput createOutput(String name, IOContext context) throws IOException {
            if (Thread.currentThread().getName().equals("held-full-flush")
                && hold.compareAndSet(true, false)) {
              entered.countDown();
              try {
                release.await();
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            }
            return super.createOutput(name, context);
          }
        }) {
      IndexWriter w =
          new IndexWriter(
              dir,
              new IndexWriterConfig()
                  .setRAMBufferSizeMB(1000)
                  .setMergePolicy(NoMergePolicy.INSTANCE));
      Term term = new Term("id", "x".repeat(1024));
      Document doc = new Document();
      doc.add(new StringField("id", term.text(), Field.Store.NO));
      doc.add(new NumericDocValuesField("ndv", 0));
      w.addDocument(doc);
      DocumentsWriterFlushControl flushControl = w.docWriter.flushControl;
      flushControl.maxDeleteBytes = 256 * 1024;
      hold.set(true);
      Thread flusher =
          new Thread(
              () -> {
                try {
                  w.flush();
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              },
              "held-full-flush");
      flusher.start();
      entered.await();
      Thread updater =
          new Thread(
              () -> {
                try {
                  for (int i = 0; i < 2000; i++) {
                    w.updateNumericDocValue(term, "ndv", i);
                  }
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
      updater.start();
      while (updater.isAlive() && flushControl.stallControl.hasBlocked() == false) {
        Thread.onSpinWait();
      }
      long deleteBytesWhileHeld = flushControl.getDeleteBytesUsed();
      release.countDown();
      flusher.join();
      updater.join();
      w.rollback();
      assertTrue("delete bytes while held: " + deleteBytesWhileHeld, deleteBytesWhileHeld < 512 * 1024);
    }
  }
}
