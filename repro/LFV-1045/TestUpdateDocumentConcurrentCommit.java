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

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.InfoStream;

public class TestUpdateDocumentConcurrentCommit extends LuceneTestCase {

  // Expected: every commit holds at most one live document per key; actual: some key has two.
  public void testAtMostOneLiveDocumentPerKey() throws Exception {
    int numThreads = 16, keysPerThread = 50;
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(
                dir,
                new IndexWriterConfig()
                    .setCodec(TestUtil.getDefaultCodec())
                    .setInfoStream(InfoStream.NO_OUTPUT)
                    .setRAMBufferSizeMB(0.25)
                    .setMergePolicy(NoMergePolicy.INSTANCE))) {
      AtomicBoolean stop = new AtomicBoolean();
      Thread[] threads = new Thread[numThreads];
      for (int t = 0; t < numThreads; t++) {
        int firstKey = t * keysPerThread;
        byte[] pad = new byte[2000 + 300 * t];
        threads[t] =
            new Thread(
                () -> {
                  try {
                    for (int i = 0; stop.get() == false; i++) {
                      String id = Integer.toString(firstKey + i % keysPerThread);
                      Document doc = new Document();
                      doc.add(new StringField("id", id, Field.Store.NO));
                      doc.add(new StoredField("pad", pad));
                      w.updateDocument(new Term("id", id), doc);
                    }
                  } catch (AlreadyClosedException e) {
                    return;
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                });
        threads[t].start();
      }
      try {
        for (int c = 0; c < 5000; c++) {
          w.commit();
          try (DirectoryReader r = DirectoryReader.open(dir)) {
            IndexSearcher s = new IndexSearcher(r);
            for (int k = 0; k < numThreads * keysPerThread; k++) {
              assertTrue("commit " + c + ", key " + k,
                  s.count(new TermQuery(new Term("id", Integer.toString(k)))) <= 1);
            }
          }
        }
      } finally {
        stop.set(true);
        for (Thread t : threads) {
          t.join();
        }
      }
    }
  }
}
