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
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestHasDeletionsDuringFlush extends LuceneTestCase {

  public void testHasDeletionsWhileCommitWritesSegment() throws Exception {
    CountDownLatch writing = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexOutput createOutput(String name, IOContext context) throws IOException {
            if (Thread.currentThread().getName().equals("committer") && writing.getCount() > 0) {
              writing.countDown();
              try {
                release.await();
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            }
            return super.createOutput(name, context);
          }
        };
    IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE));
    for (String id : new String[] {"a", "b"}) {
      Document doc = new Document();
      doc.add(new StringField("id", id, Field.Store.NO));
      w.addDocument(doc);
    }
    w.deleteDocuments(new Term("id", "a"));
    assertTrue(w.hasDeletions());

    Thread committer =
        new Thread(
            () -> {
              try {
                w.commit();
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            },
            "committer");
    committer.start();
    writing.await();
    boolean during = w.hasDeletions();
    release.countDown();
    committer.join();

    // Expected: hasDeletions() is true before, during and after the commit; actual: false during.
    assertTrue(w.hasDeletions());
    assertTrue("hasDeletions() while the commit writes the segment", during);
    w.close();
    dir.close();
  }
}
