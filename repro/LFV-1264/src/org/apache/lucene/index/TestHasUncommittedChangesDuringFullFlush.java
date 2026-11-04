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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

public class TestHasUncommittedChangesDuringFullFlush extends LuceneTestCase {

  public void testAcknowledgedDeleteDuringFullFlush() throws Exception {
    IndexWriter[] w = new IndexWriter[1];
    Thread[] updater = new Thread[1];
    AtomicBoolean addDuringCommit = new AtomicBoolean(true);
    AtomicBoolean uncommitted = new AtomicBoolean(true);
    InfoStream hooks =
        new InfoStream() {
          @Override
          public boolean isEnabled(String component) {
            return "IW".equals(component) || "DW".equals(component);
          }

          @Override
          public void message(String component, String message) {
            try {
              if (message.startsWith("now apply all deletes") && addDuringCommit.getAndSet(false)) {
                w[0].addDocuments(List.of());
              } else if (message.endsWith("flush naked frozen global deletes")) {
                updater[0].join();
                uncommitted.set(w[0].hasUncommittedChanges());
              }
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }

          @Override
          public void close() {}
        };
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig().setInfoStream(hooks))) {
      w[0] = writer;
      Document doc = new Document();
      doc.add(new StringField("id", "1", Field.Store.NO));
      writer.addDocument(doc);
      writer.commit();

      CountDownLatch inBlock = new CountDownLatch(1);
      AtomicLong seqNo = new AtomicLong();
      Iterable<Iterable<IndexableField>> emptyBlock =
          () -> {
            inBlock.countDown();
            while (writer.getDocsWriter().flushControl.isFullFlush() == false) {
              Thread.onSpinWait();
            }
            return Collections.emptyIterator();
          };
      updater[0] =
          new Thread(
              () -> {
                try {
                  seqNo.set(writer.updateDocuments(new TermQuery(new Term("id", "1")), emptyBlock));
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      updater[0].start();
      inBlock.await();
      DirectoryReader.open(writer).close();

      // expected: true once updateDocuments has returned its seqNo; actual: false
      assertTrue("seqNo=" + seqNo.get() + ", hasUncommittedChanges()=false", uncommitted.get());
    }
  }
}
