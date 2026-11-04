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
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.JavaLoggingInfoStream;

public class TestRegisterMergeLoggingFailure extends LuceneTestCase {

  public void testLoggingFailureInForceMergeDeletesDoesNotDuplicateDocuments() throws Exception {
    // a java.util.logging handler that fails once, on IndexWriter's "add merge to pendingMerges"
    AtomicBoolean armed = new AtomicBoolean();
    Handler failOnce =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            if (record.getMessage().startsWith("add merge to pendingMerges")
                && armed.compareAndSet(true, false)) {
              throw new IllegalStateException("logging backend failed");
            }
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    // the parent of the "test.lucene.<component>" loggers that JavaLoggingInfoStream writes to
    Logger logger = Logger.getLogger("test.lucene");
    logger.setUseParentHandlers(false);
    logger.setLevel(Level.WARNING);
    logger.addHandler(failOnce);
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setMergePolicy(new LogDocMergePolicy())
              .setMergeScheduler(new SerialMergeScheduler())
              .setInfoStream(new JavaLoggingInfoStream("test.lucene.", Level.WARNING));
      IndexWriter w = new IndexWriter(dir, iwc);
      // one segment of 2 documents (ids 0 and 1), id 0 deleted: 1 live document
      for (int id = 0; id < 2; id++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(id), Field.Store.NO));
        w.addDocument(doc);
      }
      w.deleteDocuments(new Term("id", "0"));
      w.commit();

      armed.set(true);
      expectThrows(IllegalStateException.class, w::forceMergeDeletes); // the writer stays open
      w.forceMerge(1); // with assertions on: AssertionError in IndexWriter._mergeInit
      w.close();

      try (DirectoryReader r = DirectoryReader.open(dir)) {
        int copiesOfId1 = new IndexSearcher(r).count(new TermQuery(new Term("id", "1")));
        assertEquals("live documents (copies of id:1: " + copiesOfId1 + ")", 1, r.numDocs());
      }
    } finally {
      logger.removeHandler(failOnce);
    }
  }
}
