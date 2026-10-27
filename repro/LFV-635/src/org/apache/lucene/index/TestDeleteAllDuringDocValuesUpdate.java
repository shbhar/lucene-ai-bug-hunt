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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * IndexWriter is documented as thread safe. updateDocValues on a field no document has used yet
 * registers the field as doc-values-only in the writer-wide field numbers and then queues the
 * update. A deleteAll() in between clears the registration, but the update is still queued and
 * applies to documents added after deleteAll. Writing the update then expects the field in the
 * writer-wide field numbers ("which is guaranteed to exist"): the NRT reopen / commit throws, which
 * closes the writer with assertions on and makes every commit and close fail with assertions off;
 * or, if the new document defined the field with another doc-values type, the update is written
 * into that field (assertions off) and the committed field cannot be read.
 *
 * <p>The latch only fixes the interleaving: the update's numericValue() is read after the field
 * was registered and before the update is queued.
 */
public class TestDeleteAllDuringDocValuesUpdate extends LuceneTestCase {

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private static final class PausingNumericField extends NumericDocValuesField {
    final CountDownLatch paused = new CountDownLatch(1);
    final CountDownLatch resume = new CountDownLatch(1);
    private final AtomicBoolean first = new AtomicBoolean(true);

    PausingNumericField(String name, long value) {
      super(name, value);
    }

    @Override
    public Number numericValue() {
      if (first.compareAndSet(true, false)) {
        paused.countDown();
        await(resume);
      }
      return super.numericValue();
    }
  }

  private interface UpdateCall {
    void run() throws Exception;
  }

  /** Runs the update in another thread; deleteAll + addDocument(doc) happen while it is paused. */
  private static void race(
      IndexWriter writer,
      PausingNumericField field,
      UpdateCall update,
      Document doc,
      boolean rejectionAllowed)
      throws Exception {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread t =
        new Thread(
            () -> {
              try {
                update.run();
              } catch (Throwable e) {
                failure.set(e);
              }
            });
    t.start();
    await(field.paused); // the field is registered, the update is not queued yet
    Thread deleteAll =
        new Thread(
            () -> {
              try {
                writer.deleteAll();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
    deleteAll.start();
    // On main, deleteAll does not wait for the paused update and finishes here; a fix may make it
    // wait for the update, so do not wait forever.
    deleteAll.join(10_000);
    if (deleteAll.isAlive()) {
      // deleteAll waits for the update: the update is ordered before deleteAll
      field.resume.countDown();
      t.join();
      deleteAll.join();
      writer.addDocument(doc);
    } else {
      writer.addDocument(doc);
      field.resume.countDown();
      t.join();
    }
    // the update may only be rejected if the document defines the field differently
    if (failure.get() != null
        && (rejectionAllowed == false || failure.get() instanceof IllegalArgumentException == false)) {
      throw new AssertionError("update failed", failure.get());
    }
  }

  private static String commitOutcome(IndexWriter writer) {
    try {
      writer.commit();
      return "ok";
    } catch (Throwable e) {
      return e + " (tragic=" + writer.getTragicException() + ", open=" + writer.isOpen() + ")";
    }
  }

  /** updateDocValues of a new field; the matched document is added after deleteAll. */
  public void testUpdateDocValuesNewField() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig());
    PausingNumericField f = new PausingNumericField("f", 42);
    Document doc = new Document();
    doc.add(new StringField("id", "1", Field.Store.NO));
    race(writer, f, () -> writer.updateDocValues(new Term("id", "1"), f), doc, false);
    String commit = commitOutcome(writer);
    // Either order is fine (the update applies to the document or is dropped by deleteAll), but
    // committing must work.
    assertEquals("commit after deleteAll raced updateDocValues", "ok", commit);
    writer.close();
    dir.close();
  }

  /** The same through softUpdateDocument: the soft-deletes field is registered first. */
  public void testSoftUpdateDocument() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriter writer =
        new IndexWriter(dir, new IndexWriterConfig().setSoftDeletesField("soft_delete"));
    PausingNumericField soft = new PausingNumericField("soft_delete", 1);
    Document doc = new Document();
    doc.add(new StringField("id", "1", Field.Store.NO));
    Document newDoc = new Document();
    newDoc.add(new StringField("id", "1", Field.Store.NO));
    race(
        writer, soft, () -> writer.softUpdateDocument(new Term("id", "1"), newDoc, soft), doc, false);
    String commit = commitOutcome(writer);
    assertEquals("commit after deleteAll raced softUpdateDocument", "ok", commit);
    writer.close();
    dir.close();
  }

  /**
   * The document added after deleteAll defines the field with SORTED doc values; the queued NUMERIC
   * update must not end up in that field.
   */
  public void testUpdateDocValuesNewFieldConflictingType() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig());
    PausingNumericField f = new PausingNumericField("f", 42);
    Document doc = new Document();
    doc.add(new StringField("id", "1", Field.Store.NO));
    doc.add(new SortedDocValuesField("f", new BytesRef("x")));
    race(writer, f, () -> writer.updateDocValues(new Term("id", "1"), f), doc, true);
    String commit = commitOutcome(writer);
    String read = "not committed";
    String check = "not run";
    if (commit.equals("ok")) {
      writer.close();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        SortedDocValues values = r.leaves().get(0).reader().getSortedDocValues("f");
        read =
            values.advanceExact(0) ? values.lookupOrd(values.ordValue()).utf8ToString() : "no value";
      } catch (Throwable e) {
        read = e.toString();
      }
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      try (CheckIndex ci = new CheckIndex(dir)) {
        ci.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS);
        ci.setThreadCount(1);
        ci.setInfoStream(new PrintStream(bos, true, StandardCharsets.UTF_8));
        check = ci.checkIndex().clean ? "clean" : "NOT clean";
      }
    }
    String observed = "commit: " + commit + "; SORTED value of f: " + read + "; CheckIndex: " + check;
    assertEquals(observed, "ok", commit);
    assertEquals(observed, "x", read);
    dir.close();
  }
}
