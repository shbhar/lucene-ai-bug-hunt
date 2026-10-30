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
import java.util.concurrent.locks.ReentrantLock;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestHasUncommittedChangesConcurrentFlush extends LuceneTestCase {

  public void testFlushBetweenChangeCountAndRamChecks() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new StringField("id", "1", Field.Store.NO));
      w.addDocument(doc);
      w.commit();
      w.deleteDocuments(new Term("id", "1"));

      ReentrantLock lock = w.getDocsWriter().deleteQueue.globalBufferLock;
      AtomicBoolean result = new AtomicBoolean();
      Thread checker = new Thread(() -> result.set(w.hasUncommittedChanges()));
      lock.lock();
      try {
        checker.start();
        while (checker.isAlive() && lock.hasQueuedThread(checker) == false) {
          Thread.onSpinWait();
        }
        w.flush();
      } finally {
        lock.unlock();
      }
      checker.join();

      // expected: true (the flushed delete is still uncommitted); actual: false
      assertTrue(result.get());
    }
  }
}
