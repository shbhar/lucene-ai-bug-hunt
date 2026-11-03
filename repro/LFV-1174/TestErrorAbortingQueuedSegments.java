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

import com.carrotsearch.randomizedtesting.annotations.Timeout;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.FilterIndexOutput;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestErrorAbortingQueuedSegments extends LuceneTestCase {

  // Expected: DirectoryReader.open throws the Error and the writer closes itself; actual: it hangs.
  @Timeout(millis = 20_000)
  public void testErrorWhileAbortingQueuedSegments() throws Exception {
    AtomicBoolean fail = new AtomicBoolean();
    FilterDirectory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexOutput createOutput(String name, IOContext context) throws IOException {
            return new FilterIndexOutput("x", name, super.createOutput(name, context)) {
              @Override
              public void close() throws IOException {
                out.close();
                if (fail.get()) throw new OutOfMemoryError("simulated");
              }
            };
          }
        };
    IndexWriter writer =
        new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()));
    CountDownLatch allInAddDocument = new CountDownLatch(3);
    Thread[] threads = new Thread[3];
    for (int i = 0; i < threads.length; i++) {
      Document doc = new Document();
      doc.add(
          new TextField(
              "f",
              new StringReader("x") {
                @Override
                public int read(char[] buf, int off, int len) throws IOException {
                  allInAddDocument.countDown();
                  try {
                    allInAddDocument.await();
                  } catch (InterruptedException e) {
                    throw new IOException(e);
                  }
                  return super.read(buf, off, len);
                }
              }));
      threads[i] =
          new Thread(
              () -> {
                try {
                  writer.addDocument(doc);
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
      threads[i].start();
    }
    for (Thread t : threads) t.join();
    fail.set(true);
    expectThrows(OutOfMemoryError.class, () -> DirectoryReader.open(writer));
    assertFalse(writer.isOpen());
  }
}
