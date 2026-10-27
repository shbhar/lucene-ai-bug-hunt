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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndexWriter#tryUpdateDocValue documents that a null value un-deletes a soft-deleted document
 * ("This can be used to un-delete a soft-deleted document since this method will apply the field
 * update even if the document is marked as deleted") and that a returned sequence number > 0 means
 * the update succeeded. If the un-delete hits a document whose segment is part of a merge that is
 * already running, the call succeeds and NRT readers show the document again, but the merge, which
 * dropped the document because it was soft-deleted when the merge started, then commits without
 * it: the acknowledged un-delete is silently lost.
 *
 * <p>Only public API is used (OneMergeWrappingMergePolicy and OneMerge#wrapForMerge to hold the
 * merge after it has taken its readers, plus a second thread for the merge).
 */
public class TestTryUndeleteLostByRunningMerge extends LuceneTestCase {

  private static final String SOFT = "soft";

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  /** Holds the next forced merge inside wrapForMerge until released. */
  private static final class PausingMerges {
    volatile CountDownLatch reached;
    volatile CountDownLatch release;

    MergePolicy wrap(MergePolicy in) {
      return new OneMergeWrappingMergePolicy(
          in,
          toWrap ->
              new MergePolicy.OneMerge(toWrap.segments) {
                @Override
                public CodecReader wrapForMerge(CodecReader reader) throws IOException {
                  CountDownLatch r = reached, g = release;
                  if (r != null && g != null) {
                    reached = null;
                    r.countDown();
                    try {
                      assertTrue("merge was never released", g.await(60, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                      throw new AssertionError(e);
                    }
                  }
                  return toWrap.wrapForMerge(reader);
                }
              });
    }
  }

  private static int docIdOf(DirectoryReader reader, String id) throws IOException {
    StoredFields sf = reader.storedFields();
    for (int i = 0; i < reader.maxDoc(); i++) { // deleted documents included
      if (id.equals(sf.document(i).get("id"))) {
        return i;
      }
    }
    return -1;
  }

  private static int hits(DirectoryReader reader, String id) throws IOException {
    return new IndexSearcher(reader).count(new TermQuery(new Term("id", id)));
  }

  private interface DuringMerge {
    void run(IndexWriter w) throws IOException;
  }

  /**
   * Builds two segments, [a, b] and [c], soft-deletes a, then runs forceMerge(1) in a second
   * thread and calls {@code during} while that merge is held after it took its readers.
   */
  private static void runWithHeldMerge(
      Directory dir, MergePolicy base, DuringMerge before, DuringMerge during, DuringMerge after)
      throws Exception {
    PausingMerges pausing = new PausingMerges();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setSoftDeletesField(SOFT)
            .setMergePolicy(pausing.wrap(base))
            .setMergeScheduler(new ConcurrentMergeScheduler());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(doc("a"));
      w.addDocument(doc("b"));
      w.flush();
      w.addDocument(doc("c"));
      w.flush();
      w.updateDocValues(new Term("id", "a"), new NumericDocValuesField(SOFT, 1)); // soft-delete a
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(2, r.numDocs()); // b, c
        assertEquals(0, hits(r, "a"));
      }
      before.run(w);

      CountDownLatch reached = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      pausing.release = release;
      pausing.reached = reached;
      AtomicReference<Throwable> mergeFailure = new AtomicReference<>();
      Thread merger =
          new Thread(
              () -> {
                try {
                  w.forceMerge(1);
                } catch (Throwable t) {
                  mergeFailure.set(t);
                }
              });
      merger.start();
      try {
        assertTrue("merge did not start", reached.await(60, TimeUnit.SECONDS));
        during.run(w);
      } finally {
        release.countDown();
        merger.join(60_000);
      }
      assertNull("merge failed: " + mergeFailure.get(), mergeFailure.get());
      after.run(w);
    }
  }

  private static void undeleteA(IndexWriter w) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(w)) {
      int docA = docIdOf(r, "a");
      assertTrue(docA >= 0);
      long seqNo = w.tryUpdateDocValue(r, docA, new NumericDocValuesField(SOFT, (Long) null));
      assertTrue("tryUpdateDocValue must succeed (segment not merged away yet)", seqNo > 0);
    }
    try (DirectoryReader r = DirectoryReader.open(w)) {
      // the un-delete is visible: a is live again
      assertEquals(1, hits(r, "a"));
      assertEquals(3, r.numDocs());
    }
  }

  private static void expectALive(IndexWriter w, Directory dir) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(w)) {
      assertEquals("un-deleted document a must stay live after the merge", 1, hits(r, "a"));
      assertEquals(3, r.numDocs());
    }
    w.commit();
    try (DirectoryReader r =
        new SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), SOFT)) {
      assertEquals("committed index must contain the un-deleted document a", 1, hits(r, "a"));
    }
  }

  /** The bug: un-delete while a merge that drops the soft-deleted document is running. */
  public void testUndeleteDuringMergeIsLost() throws Exception {
    try (Directory dir = newDirectory()) {
      runWithHeldMerge(
          dir,
          new LogDocMergePolicy(),
          w -> {},
          TestTryUndeleteLostByRunningMerge::undeleteA,
          w -> expectALive(w, dir));
    }
  }

  /**
   * Same with SoftDeletesRetentionMergePolicy: a soft-deleted document that the retention query
   * does not keep is dropped by the merge even though it was un-deleted while the merge ran.
   */
  public void testUndeleteDuringMergeIsLostWithRetentionPolicy() throws Exception {
    try (Directory dir = newDirectory()) {
      runWithHeldMerge(
          dir,
          new SoftDeletesRetentionMergePolicy(SOFT, MatchNoDocsQuery::new, new LogDocMergePolicy()),
          w -> {},
          TestTryUndeleteLostByRunningMerge::undeleteA,
          w -> expectALive(w, dir));
    }
  }

  /** Control: the same un-delete before the merge starts survives the merge. */
  public void testControlUndeleteBeforeMergeSurvives() throws Exception {
    try (Directory dir = newDirectory()) {
      runWithHeldMerge(
          dir,
          new LogDocMergePolicy(),
          TestTryUndeleteLostByRunningMerge::undeleteA,
          w -> {},
          w -> expectALive(w, dir));
    }
  }

  /** Control: a delete made while the merge runs is carried over to the merged segment. */
  public void testControlDeleteDuringMergeIsCarriedOver() throws Exception {
    try (Directory dir = newDirectory()) {
      runWithHeldMerge(
          dir,
          new LogDocMergePolicy(),
          w -> {},
          w -> {
            try (DirectoryReader r = DirectoryReader.open(w)) {
              assertTrue(w.tryDeleteDocument(r, docIdOf(r, "b")) > 0);
            }
          },
          w -> {
            try (DirectoryReader r = DirectoryReader.open(w)) {
              assertEquals(0, hits(r, "b"));
              assertEquals(1, r.numDocs());
            }
          });
    }
  }
}
