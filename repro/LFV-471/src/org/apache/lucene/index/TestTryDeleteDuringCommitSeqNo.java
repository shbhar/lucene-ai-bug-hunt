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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

/**
 * IndexWriter class javadoc: "commit also returns a sequence number, describing which changes are
 * in the commit point and which are not". A tryDeleteDocument that runs while a commit is between
 * flushAllThreads() (which fixes the commit's seqNo) and the segmentInfos clone gets a larger
 * seqNo from the new delete queue, yet its deletion is in the commit point.
 */
public class TestTryDeleteDuringCommitSeqNo extends LuceneTestCase {

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  public void testTryDeleteDuringCommit() throws Exception {
    Directory dir = newDirectory();
    Thread mainThread = Thread.currentThread();
    AtomicBoolean arm = new AtomicBoolean(false);
    AtomicLong tryDeleteSeqNo = new AtomicLong(-2);
    AtomicReference<Throwable> err = new AtomicReference<>();
    AtomicReference<IndexWriter> writerRef = new AtomicReference<>();
    AtomicReference<DirectoryReader> nrtRef = new AtomicReference<>();
    InfoStream hook =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            // inside commit's flushAllThreads: the commit seqNo is already fixed
            if (Thread.currentThread() == mainThread
                && "DWPT".equals(component)
                && message.startsWith("flush postings as segment")
                && arm.compareAndSet(true, false)) {
              Thread t =
                  new Thread(
                      () -> {
                        try {
                          tryDeleteSeqNo.set(writerRef.get().tryDeleteDocument(nrtRef.get(), 0));
                        } catch (Throwable e) {
                          err.set(e);
                        }
                      });
              t.start();
              try {
                t.join(30_000);
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return "DWPT".equals(component);
          }

          @Override
          public void close() {}
        };
    IndexWriter w =
        new IndexWriter(
            dir,
            new IndexWriterConfig(new MockAnalyzer(random()))
                .setMergePolicy(NoMergePolicy.INSTANCE)
                .setInfoStream(hook));
    writerRef.set(w);
    w.addDocument(doc("a"));
    w.commit();
    DirectoryReader nrt = DirectoryReader.open(w);
    nrtRef.set(nrt);
    assertEquals(1, nrt.numDocs());
    w.addDocument(doc("b")); // buffered, so the commit flushes a DWPT (hook point)

    arm.set(true);
    long commitSeqNo = w.commit();
    assertFalse("hook did not run", arm.get());
    assertNull(err.get());
    long t = tryDeleteSeqNo.get();
    assertTrue("tryDeleteDocument must succeed: " + t, t > 0);

    boolean aDeletedInCommit;
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      aDeletedInCommit = new IndexSearcher(r).count(new TermQuery(new Term("id", "a"))) == 0;
    }
    // The commit contains the delete iff the delete's seqNo is <= the commit's seqNo.
    assertEquals(
        "tryDelete seqNo=" + t + " commit seqNo=" + commitSeqNo + " deleteInCommit=" + aDeletedInCommit,
        t <= commitSeqNo,
        aDeletedInCommit);
    nrt.close();
    w.close();
    dir.close();
  }

  /** Same race, scheduled through a FilterDirectory (public API) instead of an InfoStream. */
  public void testTryDeleteDuringCommitDirectoryHook() throws Exception {
    Thread mainThread = Thread.currentThread();
    AtomicBoolean arm = new AtomicBoolean(false);
    AtomicLong tryDeleteSeqNo = new AtomicLong(-2);
    AtomicReference<Throwable> err = new AtomicReference<>();
    AtomicReference<IndexWriter> writerRef = new AtomicReference<>();
    AtomicReference<DirectoryReader> nrtRef = new AtomicReference<>();
    Directory dir =
        new org.apache.lucene.store.FilterDirectory(newDirectory()) {
          @Override
          public org.apache.lucene.store.IndexOutput createOutput(
              String name, org.apache.lucene.store.IOContext context) throws IOException {
            // the commit's flushAllThreads writes the buffered DWPT's files: commit seqNo is fixed
            if (Thread.currentThread() == mainThread && arm.compareAndSet(true, false)) {
              Thread t =
                  new Thread(
                      () -> {
                        try {
                          tryDeleteSeqNo.set(writerRef.get().tryDeleteDocument(nrtRef.get(), 0));
                        } catch (Throwable e) {
                          err.set(e);
                        }
                      });
              t.start();
              try {
                t.join(30_000);
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            }
            return super.createOutput(name, context);
          }
        };
    IndexWriter w =
        new IndexWriter(
            dir,
            new IndexWriterConfig(new MockAnalyzer(random())).setMergePolicy(NoMergePolicy.INSTANCE));
    writerRef.set(w);
    w.addDocument(doc("a"));
    w.commit();
    DirectoryReader nrt = DirectoryReader.open(w);
    nrtRef.set(nrt);
    w.addDocument(doc("b"));

    arm.set(true);
    long commitSeqNo = w.commit();
    assertFalse("hook did not run", arm.get());
    assertNull(err.get());
    long t = tryDeleteSeqNo.get();
    assertTrue("tryDeleteDocument must succeed: " + t, t > 0);
    boolean aDeletedInCommit;
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      aDeletedInCommit = new IndexSearcher(r).count(new TermQuery(new Term("id", "a"))) == 0;
    }
    assertEquals(
        "tryDelete seqNo=" + t + " commit seqNo=" + commitSeqNo + " deleteInCommit=" + aDeletedInCommit,
        t <= commitSeqNo,
        aDeletedInCommit);
    nrt.close();
    w.close();
    dir.close();
  }
}
