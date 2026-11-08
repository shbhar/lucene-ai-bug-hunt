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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;
import org.apache.lucene.util.ThreadInterruptedException;

public class TestDeleteAllUncommittedChangesWindow extends LuceneTestCase {

  public void testPendingDeleteStaysUncommittedWhileDeleteAllRuns() throws Exception {
    CountDownLatch parked = new CountDownLatch(1);
    CountDownLatch resume = new CountDownLatch(1);
    InfoStream parkAfterAbort =
        new InfoStream() {
          @Override
          public boolean isEnabled(String component) {
            return "DW".equals(component);
          }

          @Override
          public void message(String component, String message) {
            if (message.equals("finished lockAndAbortAll success=true")) {
              parked.countDown();
              try {
                resume.await();
              } catch (InterruptedException e) {
                throw new ThreadInterruptedException(e);
              }
            }
          }

          @Override
          public void close() {}
        };

    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setInfoStream(parkAfterAbort))) {
      Document doc = new Document();
      doc.add(new StringField("id", "1", Field.Store.NO));
      w.addDocument(doc);
      w.commit();
      DirectoryReader reader = DirectoryReader.open(w);
      w.deleteDocuments(new Term("id", "1"));
      assertTrue(w.hasUncommittedChanges() && reader.isCurrent() == false);

      Thread deleteAll =
          new Thread(
              () -> {
                try {
                  w.deleteAll();
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
      deleteAll.start();
      parked.await();
      boolean uncommitted = w.hasUncommittedChanges();
      boolean current = reader.isCurrent();
      resume.countDown();
      deleteAll.join();
      reader.close();

      // Expected: still uncommitted and not current while deleteAll() runs; actual: false and true.
      assertTrue(
          "hasUncommittedChanges()=" + uncommitted + ", isCurrent()=" + current,
          uncommitted && current == false);
    }
  }
}
