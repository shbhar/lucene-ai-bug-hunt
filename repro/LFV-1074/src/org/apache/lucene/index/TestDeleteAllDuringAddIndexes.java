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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestDeleteAllDuringAddIndexes extends LuceneTestCase {

  public void testDeleteAllWhileAddIndexesCopiesFiles() throws Exception {
    Directory src = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new NumericDocValuesField("f", 1));
      w.addDocument(doc);
    }

    CountDownLatch copying = new CountDownLatch(1);
    CountDownLatch resume = new CountDownLatch(1);
    Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public void copyFrom(Directory from, String s, String d, IOContext ctx)
              throws IOException {
            if (copying.getCount() > 0) {
              copying.countDown();
              try {
                resume.await();
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            }
            super.copyFrom(from, s, d, ctx);
          }
        };
    IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig());
    Thread adder =
        new Thread(
            () -> {
              try {
                writer.addIndexes(src);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
    adder.start();
    copying.await();
    writer.deleteAll();
    resume.countDown();
    adder.join();

    Document doc = new Document();
    doc.add(new SortedDocValuesField("f", new BytesRef("x")));
    try {
      writer.addDocument(doc);
    } catch (IllegalArgumentException ok) {
    }
    writer.close();

    // Expected: the commit opens in a new IndexWriter; actual: IAE "cannot change field "f" from NUMERIC to SORTED".
    new IndexWriter(dir, new IndexWriterConfig()).close();
    dir.close();
    src.close();
  }
}
