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
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDeleteAllRacingDocValuesUpdate extends LuceneTestCase {

  /**
   * IndexWriter reads this field's value after it has checked the field against its field schema
   * and before it queues the update. The first read pauses there until the test resumes it.
   */
  private static final class PausingNumericField extends NumericDocValuesField {
    final CountDownLatch paused = new CountDownLatch(1);
    final CountDownLatch resume = new CountDownLatch(1);

    PausingNumericField(String name, long value) {
      super(name, value);
    }

    @Override
    public Number numericValue() {
      if (paused.getCount() > 0) {
        paused.countDown();
        try {
          resume.await();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      return super.numericValue();
    }
  }

  /** deleteAll() between an update's field check and its enqueue must not break commit. */
  public void testCommitAfterDeleteAllRacesDocValuesUpdate() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    Document doc0 = new Document();
    doc0.add(new StringField("id", "0", Field.Store.NO));
    doc0.add(new NumericDocValuesField("f", 1));
    w.addDocument(doc0);
    w.commit(); // f exists in the index: a NUMERIC doc-values field

    PausingNumericField f = new PausingNumericField("f", 42);
    AtomicReference<Throwable> updateFailure = new AtomicReference<>();
    Thread updater =
        new Thread(
            () -> {
              try {
                w.updateDocValues(new Term("id", "1"), f);
              } catch (Throwable t) {
                updateFailure.set(t);
              }
            });
    updater.start();
    f.paused.await(); // the update has checked f, but is not queued yet

    Thread deleter =
        new Thread(
            () -> {
              try {
                w.deleteAll();
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
    deleter.start();
    deleter.join(10_000); // finishes at once today; a fix may make deleteAll wait for the update
    boolean deleteAllFirst = deleter.isAlive() == false;
    Document doc1 = new Document();
    doc1.add(new StringField("id", "1", Field.Store.NO));
    if (deleteAllFirst) {
      w.addDocument(doc1); // indexed after deleteAll, before the update is queued
    }
    f.resume.countDown();
    updater.join();
    deleter.join();
    if (deleteAllFirst == false) {
      w.addDocument(doc1);
    }
    assertNull(updateFailure.get());

    // Either order is legal: the update applies to doc1, or deleteAll dropped it. Commit must work.
    w.commit();
    w.close();
    dir.close();
  }
}
