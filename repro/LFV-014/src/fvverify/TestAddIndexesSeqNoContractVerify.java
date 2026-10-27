package fvverify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.InfoStream;

/**
 * Independent verification (public API only, own package) of the claim that addIndexes does not
 * respect the IndexWriter sequence-number contract.
 *
 * <p>Interleavings are forced only through IndexWriterConfig#setInfoStream callbacks (no
 * FilterDirectory, no package-private access): the addIndexes thread parks on the infoStream
 * message it emits right after its initial flush(false, true) and before it publishes the copied
 * segments; the committing thread parks on an infoStream message that it emits after its full
 * flush has fixed the commit seqNo and before it clones segmentInfos.
 */
public class TestAddIndexesSeqNoContractVerify extends LuceneTestCase {

  /** InfoStream that runs a hook when a given thread emits a message with a given prefix. */
  static final class Hooks extends InfoStream {
    static final class Hook {
      final Thread thread;
      final String component;
      final String prefix;
      final Runnable action;
      boolean fired;

      Hook(Thread thread, String component, String prefix, Runnable action) {
        this.thread = thread;
        this.component = component;
        this.prefix = prefix;
        this.action = action;
      }
    }

    final List<Hook> hooks = new ArrayList<>();
    final List<String> fired = new ArrayList<>();

    synchronized void add(Thread t, String component, String prefix, Runnable action) {
      hooks.add(new Hook(t, component, prefix, action));
    }

    @Override
    public void message(String component, String message) {
      Hook toRun = null;
      synchronized (this) {
        for (Hook h : hooks) {
          if (h.fired == false
              && h.thread == Thread.currentThread()
              && h.component.equals(component)
              && message.startsWith(h.prefix)) {
            h.fired = true;
            fired.add(component + ":" + h.prefix);
            toRun = h;
            break;
          }
        }
      }
      if (toRun != null) {
        toRun.action.run(); // run outside our own monitor
      }
    }

    @Override
    public boolean isEnabled(String component) {
      return true;
    }

    @Override
    public void close() {}
  }

  static IndexWriterConfig iwc(InfoStream is) {
    IndexWriterConfig c = new IndexWriterConfig();
    c.setMergePolicy(NoMergePolicy.INSTANCE);
    c.setMergeScheduler(new SerialMergeScheduler());
    c.setUseCompoundFile(false);
    c.setCommitOnClose(false);
    if (is != null) c.setInfoStream(is);
    return c;
  }

  static Document doc(String id, long v) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.NO));
    d.add(new NumericDocValuesField("val", v));
    return d;
  }

  /** Source index: a single committed doc id=k val=100. */
  static Directory source() throws IOException {
    Directory d = new ByteBuffersDirectory();
    try (IndexWriter sw = new IndexWriter(d, iwc(null))) {
      sw.addDocument(doc("k", 100));
      sw.commit();
    }
    return d;
  }

  /** Sorted list of the values of all LIVE docs. */
  static List<Long> liveValues(DirectoryReader r) throws IOException {
    List<Long> out = new ArrayList<>();
    for (LeafReaderContext ctx : r.leaves()) {
      Bits live = ctx.reader().getLiveDocs();
      NumericDocValues dv = ctx.reader().getNumericDocValues("val");
      for (int d = 0; d < ctx.reader().maxDoc(); d++) {
        if (live != null && live.get(d) == false) continue;
        assertTrue(dv.advanceExact(d));
        out.add(dv.longValue());
      }
    }
    out.sort(null);
    return out;
  }

  static void await(CountDownLatch l) {
    try {
      assertTrue("latch timeout", l.await(30, TimeUnit.SECONDS));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  interface IOOp {
    long run() throws IOException;
  }

  /** Concurrent run: addIndexes parks after its initial flush; main runs {@code op}; release. */
  static long[] concurrentOpDuringAddIndexes(
      IndexWriter w, Hooks hooks, String parkComponent, String parkPrefix, IOOp addIndexesCall, IOOp op)
      throws Exception {
    CountDownLatch parked = new CountDownLatch(1);
    CountDownLatch go = new CountDownLatch(1);
    AtomicLong addSeq = new AtomicLong(Long.MIN_VALUE);
    AtomicReference<Throwable> err = new AtomicReference<>();
    Thread t =
        new Thread(
            () -> {
              try {
                addSeq.set(addIndexesCall.run());
              } catch (Throwable e) {
                err.set(e);
              }
            },
            "addIndexes-thread");
    hooks.add(
        t,
        parkComponent,
        parkPrefix,
        () -> {
          parked.countDown();
          await(go);
        });
    t.start();
    await(parked);
    long opSeq = op.run();
    go.countDown();
    t.join();
    if (err.get() != null) throw new AssertionError(err.get());
    return new long[] {opSeq, addSeq.get()};
  }

  // ---------------- Finding 1: buffered delete with smaller seqNo hits added docs --------------

  public void testDeleteByTermWithSmallerSeqNoDeletesDocAddedByAddIndexes() throws Exception {
    Hooks hooks = new Hooks();
    try (Directory src = source();
        Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(hooks))) {
      w.addDocument(doc("k", 1));
      w.commit();
      long[] s =
          concurrentOpDuringAddIndexes(
              w,
              hooks,
              "IW",
              "addIndexes: process directory",
              () -> w.addIndexes(src),
              () -> w.deleteDocuments(new Term("id", "k")));
      System.out.println("[delete] deleteSeqNo=" + s[0] + " addIndexesSeqNo=" + s[1] + " hooks=" + hooks.fired);
      assertTrue("seqNos: delete " + s[0] + " < addIndexes " + s[1], s[0] < s[1]);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        List<Long> v = liveValues(r);
        System.out.println("[delete] live values after = " + v + " maxDoc=" + r.maxDoc());
        assertEquals(
            "delete(seqNo " + s[0] + ") is ordered before addIndexes(seqNo " + s[1]
                + "): only the pre-existing doc (val=1) may be deleted",
            List.of(100L),
            v);
      }
    }
  }

  public void testUpdateDocumentWithSmallerSeqNoDeletesDocAddedByAddIndexes() throws Exception {
    Hooks hooks = new Hooks();
    try (Directory src = source();
        Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(hooks))) {
      w.addDocument(doc("k", 1));
      w.commit();
      long[] s =
          concurrentOpDuringAddIndexes(
              w,
              hooks,
              "IW",
              "addIndexes: process directory",
              () -> w.addIndexes(src),
              () -> w.updateDocument(new Term("id", "k"), doc("k", 2)));
      System.out.println("[updateDocument] updSeqNo=" + s[0] + " addIndexesSeqNo=" + s[1]);
      assertTrue(s[0] < s[1]);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        List<Long> v = liveValues(r);
        System.out.println("[updateDocument] live values after = " + v);
        // update(seqNo s0): deletes val=1, adds val=2; then addIndexes(seqNo s1) adds val=100.
        assertEquals(List.of(2L, 100L), v);
      }
    }
  }

  // ---------------- Finding 2: buffered DV update with smaller seqNo rewrites added docs ------

  public void testNumericDVUpdateWithSmallerSeqNoRewritesDocAddedByAddIndexes() throws Exception {
    Hooks hooks = new Hooks();
    try (Directory src = source();
        Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(hooks))) {
      w.addDocument(doc("k", 1));
      w.commit();
      long[] s =
          concurrentOpDuringAddIndexes(
              w,
              hooks,
              "IW",
              "addIndexes: process directory",
              () -> w.addIndexes(src),
              () -> w.updateNumericDocValue(new Term("id", "k"), "val", 7));
      System.out.println("[dvUpdate] updSeqNo=" + s[0] + " addIndexesSeqNo=" + s[1]);
      assertTrue(s[0] < s[1]);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        List<Long> v = liveValues(r);
        System.out.println("[dvUpdate] live values after = " + v);
        assertEquals("pre-existing doc -> 7, added doc keeps 100", List.of(7L, 100L), v);
      }
    }
  }

  /** Same as finding 1, but via addIndexes(CodecReader...). */
  public void testDeleteWithSmallerSeqNoDeletesDocAddedByAddIndexesCodecReader() throws Exception {
    Hooks hooks = new Hooks();
    try (Directory src = source();
        DirectoryReader srcReader = DirectoryReader.open(src);
        Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(hooks))) {
      w.addDocument(doc("k", 1));
      w.commit();
      CodecReader[] leaves =
          srcReader.leaves().stream().map(c -> (CodecReader) c.reader()).toArray(CodecReader[]::new);
      long[] s =
          concurrentOpDuringAddIndexes(
              w,
              hooks,
              "IW",
              "merged new segment",
              () -> w.addIndexes(leaves),
              () -> w.deleteDocuments(new Term("id", "k")));
      System.out.println("[delete/CodecReader] deleteSeqNo=" + s[0] + " addIndexesSeqNo=" + s[1] + " hooks=" + hooks.fired);
      assertTrue(s[0] < s[1]);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        List<Long> v = liveValues(r);
        System.out.println("[delete/CodecReader] live values after = " + v);
        assertEquals(List.of(100L), v);
      }
    }
  }

  // ---------------- Finding 3: commit point contains addIndexes with seqNo > commit seqNo ------

  private void runCommitRace(String commitComponent, String commitPrefix) throws Exception {
    Hooks hooks = new Hooks();
    try (Directory src = source();
        Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(hooks))) {
      w.addDocument(doc("x", 1));
      w.commit();

      CountDownLatch parked = new CountDownLatch(1);
      CountDownLatch go = new CountDownLatch(1);
      AtomicLong addSeq = new AtomicLong(Long.MIN_VALUE);
      AtomicReference<Throwable> err = new AtomicReference<>();
      Thread t =
          new Thread(
              () -> {
                try {
                  addSeq.set(w.addIndexes(src));
                } catch (Throwable e) {
                  err.set(e);
                }
              },
              "addIndexes-thread");
      hooks.add(
          t,
          "IW",
          "addIndexes: process directory",
          () -> {
            parked.countDown();
            await(go);
          });
      t.start();
      await(parked); // addIndexes has done its initial flush, has not published yet

      w.addDocument(doc("y", 2)); // pending change (so commit's full flush flushes a DWPT)

      final Thread committer = Thread.currentThread();
      final boolean[] addIndexesFinishedInsideCommitWindow = new boolean[1];
      hooks.add(
          committer,
          commitComponent,
          commitPrefix,
          () -> {
            // commit seqNo is already fixed (markForFullFlush done); let addIndexes run. Wait at
            // most 2 s: a writer that correctly serializes addIndexes with commit would block
            // addIndexes here, and the commit must then go on (no deadlock in the test).
            go.countDown();
            try {
              t.join(2_000);
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
            addIndexesFinishedInsideCommitWindow[0] = t.isAlive() == false;
          });
      long commitSeq = w.commit();
      t.join();
      if (err.get() != null) throw new AssertionError(err.get());
      System.out.println(
          "[commit/" + commitPrefix + "] commitSeqNo=" + commitSeq + " addIndexesSeqNo=" + addSeq.get()
              + " addIndexesFinishedInsideCommitWindow=" + addIndexesFinishedInsideCommitWindow[0]
              + " hooks=" + hooks.fired);
      assertTrue("commit hook fired", hooks.fired.contains(commitComponent + ":" + commitPrefix));
      try (DirectoryReader committed = DirectoryReader.open(dir)) {
        List<Long> v = liveValues(committed);
        System.out.println("[commit/" + commitPrefix + "] committed live values = " + v);
        assertTrue("docs with seqNo <= commit seqNo are committed", v.contains(1L) && v.contains(2L));
        // commit() javadoc: "All sequence numbers <= this value will be reflected in the commit,
        // and all others will not."
        assertEquals(
            "addIndexes(seqNo " + addSeq.get() + ") in commit(seqNo " + commitSeq + ") iff its seqNo <= commit seqNo",
            addSeq.get() <= commitSeq,
            v.contains(100L));
      }
    }
  }

  public void testCommitContainsAddIndexesWithLargerSeqNo_hookDuringDWPTFlush() throws Exception {
    runCommitRace("DWPT", "flush postings as segment");
  }

  public void testCommitContainsAddIndexesWithLargerSeqNo_hookBeforeApplyDeletes() throws Exception {
    runCommitRace("IW", "now apply all deletes");
  }

  // ---------------- Controls (sequential) ----------------

  public void testControlSequentialDeleteThenAddIndexes() throws Exception {
    try (Directory src = source();
        Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(null))) {
      w.addDocument(doc("k", 1));
      w.commit();
      long del = w.deleteDocuments(new Term("id", "k"));
      long add = w.addIndexes(src);
      assertTrue(del < add);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(List.of(100L), liveValues(r));
      }
    }
  }

  public void testControlSequentialAddIndexesThenDelete() throws Exception {
    try (Directory src = source();
        Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(null))) {
      w.addDocument(doc("k", 1));
      w.commit();
      long add = w.addIndexes(src);
      long del = w.deleteDocuments(new Term("id", "k"));
      assertTrue(add < del);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(List.of(), liveValues(r));
      }
    }
  }

  public void testControlSequentialCommitThenAddIndexes() throws Exception {
    try (Directory src = source();
        Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(null))) {
      w.addDocument(doc("x", 1));
      w.addDocument(doc("y", 2));
      long c = w.commit();
      long add = w.addIndexes(src);
      assertTrue(add > c);
      try (DirectoryReader committed = DirectoryReader.open(dir)) {
        assertEquals(List.of(1L, 2L), liveValues(committed));
      }
    }
  }
}
