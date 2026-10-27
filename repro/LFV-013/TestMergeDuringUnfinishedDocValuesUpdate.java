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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.InfoStream;

/**
 * A merge must not fail because another thread is in the middle of applying a doc-values update.
 * Public API only.
 *
 * <p>Scenario:
 *
 * <ol>
 *   <li>Committed segments _0 = {a} and _1 = {b}, fields id and num (NUMERIC).
 *   <li>Early update: updateNumericDocValue(a, num, 7), then add c with a BINARY field tag and
 *       flushNextBuffer(), giving _2 = {c}. The update of a is resolved but not yet written.
 *   <li>Late update on another thread: updateBinaryDocValue(a, tag) or a soft delete of a (field
 *       del), then add d and flushNextBuffer(). Neither _0 nor _1 has tag or del. That thread is
 *       held (InfoStream hook) after its packet's updates were added to _0 and before the packet is
 *       marked finished.
 *   <li>The main thread merges _0 and _1.
 * </ol>
 *
 * Every test asserts the correct behaviour; the three bug tests fail on affected versions.
 */
public class TestMergeDuringUnfinishedDocValuesUpdate extends LuceneTestCase {

  /** Logged by the applying thread between applying its packet and marking it finished. */
  private static final String HOLD_AT = "done inner apply del packet";

  /** Merges the named segments once, when armed; never merges otherwise. */
  private static final class OneShotMergePolicy extends FilterMergePolicy {
    volatile List<String> names;

    OneShotMergePolicy() {
      super(NoMergePolicy.INSTANCE);
    }

    @Override
    public MergeSpecification findMerges(
        MergeTrigger trigger, SegmentInfos infos, MergeContext ctx) {
      List<String> want = names;
      if (want == null) {
        return null;
      }
      names = null;
      List<SegmentCommitInfo> picked = new ArrayList<>();
      for (SegmentCommitInfo sci : infos) {
        if (want.contains(sci.info.name)) {
          picked.add(sci);
        }
      }
      if (picked.size() != want.size()) {
        throw new IllegalStateException("harness: expected segments " + want + " in " + infos);
      }
      MergeSpecification spec = new MergeSpecification();
      spec.add(new OneMerge(picked));
      return spec;
    }
  }

  /** Holds thread {@code victim} the first time it logs {@link #HOLD_AT}. */
  private static final class HoldingInfoStream extends InfoStream {
    volatile Thread victim;
    final AtomicBoolean held = new AtomicBoolean();
    final CountDownLatch reached = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);

    @Override
    public void message(String component, String message) {
      if (Thread.currentThread() == victim
          && message.contains(HOLD_AT)
          && held.compareAndSet(false, true)) {
        reached.countDown();
        try {
          release.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }

    @Override
    public boolean isEnabled(String component) {
      return "BD".equals(component);
    }

    @Override
    public void close() {}
  }

  private enum Late {
    BINARY_UPDATE_OF_NEW_FIELD,
    SOFT_DELETE,
    NUMERIC_UPDATE_OF_EXISTING_FIELD
  }

  private static final class Outcome {
    Throwable mergeError;
    Throwable tragic;
    Throwable lateError;
    boolean writerOpen;
    String checkIndexOfSeg0InWindow;
    String finalState;
  }

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new NumericDocValuesField("num", 0));
    return d;
  }

  private Outcome run(boolean earlyUpdate, Late late, boolean holdLatePacket, boolean nrtProbe)
      throws Exception {
    OneShotMergePolicy mp = new OneShotMergePolicy();
    HoldingInfoStream is = new HoldingInfoStream();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setMergePolicy(mp)
            .setMergeScheduler(new SerialMergeScheduler())
            .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            .setRAMBufferSizeMB(256)
            .setInfoStream(is);
    if (late == Late.SOFT_DELETE) {
      iwc.setSoftDeletesField("del");
    }
    Outcome out = new Outcome();
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, iwc);
      try {
        w.addDocument(doc("a"));
        w.commit(); // _0 = {a}
        w.addDocument(doc("b"));
        w.commit(); // _1 = {b}
        if (earlyUpdate) {
          w.updateNumericDocValue(new Term("id", "a"), "num", 7);
        }
        Document c = doc("c");
        c.add(new BinaryDocValuesField("tag", new BytesRef("c-tag")));
        w.addDocument(c);
        assertTrue(w.flushNextBuffer()); // _2 = {c}, the only segment with field tag

        AtomicReference<Throwable> lateError = new AtomicReference<>();
        Thread lateUpdater =
            new Thread(
                () -> {
                  try {
                    Term a = new Term("id", "a");
                    switch (late) {
                      case BINARY_UPDATE_OF_NEW_FIELD ->
                          w.updateBinaryDocValue(a, "tag", new BytesRef("a-tag"));
                      case SOFT_DELETE ->
                          w.softUpdateDocument(a, doc("a"), new NumericDocValuesField("del", 1));
                      case NUMERIC_UPDATE_OF_EXISTING_FIELD -> w.updateNumericDocValue(a, "num", 9);
                    }
                    w.addDocument(doc("d"));
                    w.flushNextBuffer(); // freezes the late packet and applies it on this thread
                  } catch (Throwable t) {
                    lateError.set(t);
                  }
                },
                "late-updater");
        if (holdLatePacket) {
          is.victim = lateUpdater;
        }
        lateUpdater.start();
        try {
          if (holdLatePacket) {
            if (is.reached.await(60, TimeUnit.SECONDS) == false) {
              throw new IllegalStateException(
                  "harness: late updater never logged '" + HOLD_AT + "'");
            }
          } else {
            lateUpdater.join();
          }
          try {
            if (nrtProbe) {
              // writes the resolved doc-values updates, but does not wait for the late packet
              try (DirectoryReader r = DirectoryReader.open(w, false, false)) {
                for (LeafReaderContext ctx : r.leaves()) {
                  SegmentReader leaf = (SegmentReader) ctx.reader();
                  if (leaf.getSegmentName().equals("_0")) {
                    Throwable err = CheckIndex.testDocValues(leaf, null, false).error;
                    out.checkIndexOfSeg0InWindow = err == null ? "OK" : err.toString();
                  }
                }
              }
            }
            mp.names = List.of("_0", "_1");
            w.maybeMerge(); // SerialMergeScheduler: the merge runs on this thread
          } catch (Throwable t) {
            out.mergeError = t;
          }
        } finally {
          is.release.countDown();
          lateUpdater.join();
        }
        out.lateError = lateError.get();
        out.tragic = w.getTragicException();
        out.writerOpen = w.isOpen();
        if (out.writerOpen && out.mergeError == null && out.lateError == null) {
          out.finalState = finalState(w);
        }
      } finally {
        if (w.isOpen()) {
          w.close();
        }
      }
    }
    return out;
  }

  /** "numDocs=N a.num=V a.tag=T" for the live document(s) with id a, from a fresh NRT reader. */
  private static String finalState(IndexWriter w) throws Exception {
    try (DirectoryReader r = DirectoryReader.open(w)) {
      StringBuilder sb = new StringBuilder("numDocs=").append(r.numDocs());
      for (LeafReaderContext ctx : r.leaves()) {
        LeafReader leaf = ctx.reader();
        Bits live = leaf.getLiveDocs();
        StoredFields stored = leaf.storedFields();
        NumericDocValues num = leaf.getNumericDocValues("num");
        BinaryDocValues tag = leaf.getBinaryDocValues("tag");
        for (int d = 0; d < leaf.maxDoc(); d++) {
          if ((live == null || live.get(d)) && "a".equals(stored.document(d).get("id"))) {
            sb.append(" a.num=")
                .append(num != null && num.advanceExact(d) ? num.longValue() : "none");
            sb.append(" a.tag=");
            sb.append(
                tag != null && tag.advanceExact(d) ? tag.binaryValue().utf8ToString() : "none");
          }
        }
      }
      return sb.toString();
    }
  }

  private static void assertMergeSucceeded(Outcome o, String expectedFinalState) {
    if (o.mergeError != null) {
      throw new AssertionError(
          "merge failed: "
              + o.mergeError
              + " (tragic exception: "
              + o.tragic
              + ", writer open: "
              + o.writerOpen
              + ")",
          o.mergeError);
    }
    assertNull("tragic exception", o.tragic);
    assertTrue("writer is open", o.writerOpen);
    assertNull("late updater error", o.lateError);
    assertEquals(expectedFinalState, o.finalState);
  }

  /** Bug: late BINARY update of a field that no merging segment has. */
  public void testBinaryUpdateOfFieldNewToSegment() throws Exception {
    Outcome o = run(true, Late.BINARY_UPDATE_OF_NEW_FIELD, true, false);
    assertMergeSucceeded(o, "numDocs=4 a.num=7 a.tag=a-tag");
  }

  /** Bug: late soft delete; no merging segment has the soft-deletes field yet. */
  public void testSoftDeleteFieldNewToSegment() throws Exception {
    Outcome o = run(true, Late.SOFT_DELETE, true, false);
    // the old a (num=7) is soft-deleted; the live docs are the new a (num=0), b, c and d
    assertMergeSucceeded(o, "numDocs=4 a.num=0 a.tag=none");
  }

  /** Bug: an NRT reader opened inside the window must pass CheckIndex (then the merge runs). */
  public void testNrtReaderInWindowPassesCheckIndex() throws Exception {
    Outcome o = run(true, Late.BINARY_UPDATE_OF_NEW_FIELD, true, true);
    assertEquals("CheckIndex.testDocValues on _0", "OK", o.checkIndexOfSeg0InWindow);
    assertMergeSucceeded(o, "numDocs=4 a.num=7 a.tag=a-tag");
  }

  /** Control: same window, but the late update is of a field that _0 already has. */
  public void testControlLateUpdateOfExistingField() throws Exception {
    Outcome o = run(true, Late.NUMERIC_UPDATE_OF_EXISTING_FIELD, true, false);
    assertMergeSucceeded(o, "numDocs=4 a.num=9 a.tag=none");
  }

  /** Control: same window, but no earlier resolved-and-unwritten update on _0. */
  public void testControlNoEarlyUpdate() throws Exception {
    Outcome o = run(false, Late.BINARY_UPDATE_OF_NEW_FIELD, true, false);
    assertMergeSucceeded(o, "numDocs=4 a.num=0 a.tag=a-tag");
  }

  /** Control: the late packet is finished before the merge starts (no window). */
  public void testControlLatePacketFinishedBeforeMerge() throws Exception {
    Outcome o = run(true, Late.BINARY_UPDATE_OF_NEW_FIELD, false, false);
    assertMergeSucceeded(o, "numDocs=4 a.num=7 a.tag=a-tag");
  }
}
