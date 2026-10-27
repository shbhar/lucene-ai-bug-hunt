/*
 * Independent verification (2026-10-22) of the candidate bug
 * "concurrent merge commit silently loses a doc-values update (or trips assertNoDupGen)".
 *
 * Written from the code, not copied from the reporter's repro. Differences from the reporter's test:
 *  - public API only (test lives outside org.apache.lucene.index);
 *  - the merge is held back in a merged-segment warmer (IndexWriterConfig#setMergedSegmentWarmer), i.e. after
 *    the merge baseline was taken and the merged segment was written, before commitMerge;
 *  - packets are frozen/applied by the default FlushByRamOrCountsPolicy (RAM buffer set very small at runtime
 *    via the live config), not a custom FlushPolicy and not flushNextBuffer();
 *  - the "written to disk during the merge" doc-values change is made durable by opening an NRT reader
 *    (IndexWriter#getReader -> ReaderPool#writeAllDocValuesUpdates), not by flush();
 *  - indexing thread A is paused at the BD message "applyDocValuesUpdates ..." emitted by
 *    FrozenBufferedUpdates#applyDocValuesUpdates (after it resolved its packet on the source segments,
 *    before the packet is marked finished), not at "done inner apply del packet".
 *
 * Sequence (all on one IndexWriter):
 *   _0 = {d1(key=k0,n=0), d2(key=k1,n=0)}, _1 = {d3(key=k1,n=0)}
 *   merge M(_0,_1) starts on thread C, takes its baseline, writes the merged segment, blocks in the warmer
 *   main: updateNumericDocValue(k0, n=1); NRT open  -> d1=1 is written to _0 on disk (after the baseline)
 *   A   : updateNumericDocValue(k0, n=2)            -> packet PA resolves d1=2 on _0, then A is paused
 *   main: [gap] updateNumericDocValue(no-such-key, n=7) (packet that matches nothing; only consumes a delGen)
 *   main: updateNumericDocValue(k1, n=9)            -> packet PL (later delGen) resolves d2,d3 and finishes
 *   C   : warmer returns -> commitMerge (PA still running, PL finished)
 *   A   : resumes, sees the merge, re-resolves PA on the merged segment
 * Sequential semantics: d1 was set to 1 and then to 2, so d1 must read 2.
 */
package fvverify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterMergePolicy;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.MergeScheduler;
import org.apache.lucene.index.MergeTrigger;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.InfoStream;

public class TestMergeCommitDVCarryOverRace extends LuceneTestCase {

  /** A one-shot barrier: the blocked thread signals {@code reached} and waits for {@code release}. */
  static final class Gate {
    final CountDownLatch reached = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);

    void block() {
      reached.countDown();
      try {
        if (release.await(60, TimeUnit.SECONDS) == false) {
          throw new IllegalStateException("gate timeout");
        }
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    void awaitReached() throws InterruptedException {
      assertTrue("gate not reached", reached.await(30, TimeUnit.SECONDS));
    }
  }

  /** Logs BD/IW messages; blocks {@code target} once, right after it resolved a DV-update packet. */
  static final class PausingInfoStream extends InfoStream {
    volatile Thread target;
    final Gate gate = new Gate();
    final List<String> log = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void message(String component, String message) {
      log.add(Thread.currentThread().getName() + " " + component + ": " + message);
      if (Thread.currentThread() == target
          && "BD".equals(component)
          && message.startsWith("applyDocValuesUpdates")) {
        target = null;
        gate.block();
      }
    }

    @Override
    public boolean isEnabled(String component) {
      return "BD".equals(component) || "IW".equals(component);
    }

    @Override
    public void close() {}
  }

  /** Returns a merge of all segments exactly once, on an explicit maybeMerge(); otherwise nothing. */
  static final class MergeAllOnRequest extends FilterMergePolicy {
    volatile boolean requested;

    MergeAllOnRequest() {
      super(NoMergePolicy.INSTANCE);
    }

    @Override
    public MergeSpecification findMerges(
        MergeTrigger trigger, SegmentInfos infos, MergeContext ctx) {
      if (requested == false || trigger != MergeTrigger.EXPLICIT || infos.size() < 2) {
        return null;
      }
      requested = false;
      MergeSpecification spec = new MergeSpecification();
      spec.add(new MergePolicy.OneMerge(new ArrayList<>(infos.asList())));
      return spec;
    }
  }

  /** Only records the merge source; the test runs the merge on its own thread. */
  static final class CapturingMergeScheduler extends MergeScheduler {
    volatile MergeSource source;

    @Override
    public void merge(MergeSource mergeSource, MergeTrigger trigger) {
      source = mergeSource;
    }

    @Override
    public void close() {}
  }

  static Document doc(String id, String key, long n) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new StringField("key", key, Field.Store.NO));
    d.add(new NumericDocValuesField("n", n));
    return d;
  }

  static Map<String, Long> values(DirectoryReader r) throws IOException {
    Map<String, Long> m = new TreeMap<>();
    for (LeafReaderContext ctx : r.leaves()) {
      LeafReader lr = ctx.reader();
      Bits live = lr.getLiveDocs();
      NumericDocValues n = lr.getNumericDocValues("n");
      StoredFields sf = lr.storedFields();
      for (int d = 0; d < lr.maxDoc(); d++) {
        if (live != null && live.get(d) == false) {
          continue;
        }
        Long v = (n != null && n.advanceExact(d)) ? n.longValue() : null;
        m.put(sf.document(d).get("id"), v);
      }
    }
    return m;
  }

  /** Scenario switches. */
  record Opts(
      String tag,
      boolean concurrentMerge, // merge M(_0,_1) running across the race window
      boolean d1ChangedOnDiskAfterBaseline, // d1=1 written while M runs (else: before M starts)
      boolean gapPacket, // a no-match packet between PA and PL (makes gen(PL)-1 > gen(PA))
      boolean laterPacket) {} // PL: a later finished packet on the same segment/field

  record Outcome(Map<String, Long> nrt, Map<String, Long> afterCommit, Throwable aFailure) {}

  Outcome run(Opts o) throws Exception {
    PausingInfoStream infoStream = new PausingInfoStream();
    MergeAllOnRequest mp = new MergeAllOnRequest();
    CapturingMergeScheduler ms = new CapturingMergeScheduler();
    Gate warmGate = new Gate();
    IndexWriterConfig cfg =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(mp)
            .setMergeScheduler(ms)
            .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            .setRAMBufferSizeMB(256)
            .setReaderPooling(true)
            .setMergedSegmentWarmer(reader -> warmGate.block())
            .setInfoStream(infoStream);
    Directory dir = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(dir, cfg);
    Throwable aFailure = null;
    long seqNo1 = -1;
    java.util.concurrent.atomic.AtomicLong seqNoA = new java.util.concurrent.atomic.AtomicLong(-1);
    Map<String, Long> nrt = null;
    Map<String, Long> afterCommit = null;
    try {
      w.addDocument(doc("d1", "k0", 0));
      w.addDocument(doc("d2", "k1", 0));
      w.flush();
      w.addDocument(doc("d3", "k1", 0));
      w.flush();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(2, r.leaves().size());
      }

      // From now on every delete/update makes FlushByRamOrCountsPolicy set applyAllDeletes, so each
      // update call freezes its own packet and the calling thread resolves it (live config setting).
      w.getConfig().setRAMBufferSizeMB(0.00001);

      if (o.d1ChangedOnDiskAfterBaseline == false) {
        seqNo1 = w.updateNumericDocValue(new Term("key", "k0"), "n", 1L);
        DirectoryReader.open(w).close(); // writes d1=1 to _0 before the merge baseline
      }

      Thread c = null;
      AtomicReference<Throwable> errC = new AtomicReference<>();
      if (o.concurrentMerge) {
        mp.requested = true;
        w.maybeMerge();
        MergePolicy.OneMerge merge = ms.source.getNextMerge();
        assertNotNull("merge registered", merge);
        c =
            new Thread(
                () -> {
                  try {
                    ms.source.merge(merge);
                  } catch (Throwable t) {
                    errC.set(t);
                  }
                },
                "C-merge");
        c.start();
        warmGate.awaitReached(); // merged segment written; commitMerge not yet run
      }

      if (o.d1ChangedOnDiskAfterBaseline) {
        seqNo1 = w.updateNumericDocValue(new Term("key", "k0"), "n", 1L);
        try (DirectoryReader r = DirectoryReader.open(w)) { // writes d1=1 to _0 (gen advances)
          assertEquals(Long.valueOf(1), values(r).get("d1"));
        }
      }

      AtomicReference<Throwable> errA = new AtomicReference<>();
      Thread a =
          new Thread(
              () -> {
                try {
                  seqNoA.set(w.updateNumericDocValue(new Term("key", "k0"), "n", 2L)); // PA
                } catch (Throwable t) {
                  errA.set(t);
                }
              },
              "A-indexer");
      infoStream.target = a;
      a.start();
      infoStream.gate.awaitReached(); // PA resolved on _0/_1, not finished

      if (o.gapPacket) {
        w.updateNumericDocValue(new Term("key", "no-such-key"), "n", 7L);
      }
      if (o.laterPacket) {
        w.updateNumericDocValue(new Term("key", "k1"), "n", 9L); // packet PL, finishes
      }

      if (c != null) {
        warmGate.release.countDown();
        c.join(30_000);
        assertFalse(c.isAlive());
        assertNull("merge thread failed: " + errC.get(), errC.get());
      }
      infoStream.gate.release.countDown();
      a.join(30_000);
      assertFalse(a.isAlive());
      aFailure = errA.get();
      System.out.println(
          "[" + o.tag + "] seqNo(d1:=1)=" + seqNo1 + " seqNo(d1:=2, thread A)=" + seqNoA.get());
      if (aFailure != null) {
        System.out.println(
            "["
                + o.tag
                + "] thread A failed: "
                + aFailure
                + "; writer open="
                + w.isOpen()
                + " tragicException="
                + w.getTragicException());
      }
      if (aFailure == null) {
        try (DirectoryReader r = DirectoryReader.open(w)) {
          nrt = values(r);
        }
        w.commit();
        w.close();
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          afterCommit = values(r);
        }
      }
    } finally {
      java.nio.file.Files.write(
          java.nio.file.Path.of("infostream-" + o.tag + ".log"), new ArrayList<>(infoStream.log));
      if (w.isOpen()) {
        try {
          w.rollback();
        } catch (Throwable t) {
          // writer may be tragically closed
        }
      }
      dir.close();
    }
    return new Outcome(nrt, afterCommit, aFailure);
  }

  static void check(Opts o, Outcome out) {
    if (out.aFailure != null) {
      throw new AssertionError(
          "[" + o.tag + "] thread A updateNumericDocValue(k0, 2) failed: " + out.aFailure,
          out.aFailure);
    }
    long k1 = o.laterPacket ? 9L : 0L;
    Map<String, Long> expected = Map.of("d1", 2L, "d2", k1, "d3", k1);
    System.out.println(
        "[" + o.tag + "] expected=" + expected + " nrt=" + out.nrt + " afterCommit=" + out.afterCommit);
    assertEquals(
        "[" + o.tag + "] d1 was set to 1 then 2; nrt=" + out.nrt + " afterCommit=" + out.afterCommit,
        List.of(expected, expected),
        java.util.Arrays.asList(out.nrt, out.afterCommit));
  }

  /** Bug, non-adjacent gens: diskDelGen = gen(PL)-1 > gen(PA): silent lost update expected on main. */
  public void testLostUpdate() throws Exception {
    Opts o = new Opts("lost-update", true, true, true, true);
    check(o, run(o));
  }

  /** Bug, adjacent gens: diskDelGen = gen(PL)-1 == gen(PA): duplicate delGen on the merged seg. */
  public void testAdjacentGens() throws Exception {
    Opts o = new Opts("adjacent", true, true, false, true);
    check(o, run(o));
  }

  /** Control: same updates, no concurrent merge. */
  public void testControlNoMerge() throws Exception {
    Opts o = new Opts("ctl-nomerge", false, true, true, true);
    check(o, run(o));
  }

  /** Control: concurrent merge, but no later finished packet on the segment/field. */
  public void testControlNoLaterPacket() throws Exception {
    Opts o = new Opts("ctl-nolater", true, true, true, false);
    check(o, run(o));
  }

  /** Control: concurrent merge + later packet, but d1's first change is in the merge baseline. */
  public void testControlNoDiskChangeDuringMerge() throws Exception {
    Opts o = new Opts("ctl-nodisk", true, false, true, true);
    check(o, run(o));
  }
}
