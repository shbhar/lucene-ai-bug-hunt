import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;

/**
 * R6: SegmentInfos.version++ runs under two different monitors. IndexWriter.newSegmentName()
 * (IW:2169-2181) bumps it holding only synchronized(segmentInfos); checkpoint()/changed()/
 * setLiveCommitData()/... (IW:2908-2926, IW:4167-4175) bump it holding only
 * synchronized(IndexWriter).
 *
 * <p>Run with -javaagent:agent/r6agent.jar. The agent inserts a no-op hook between the read and the
 * write of version++ in SegmentInfos.changed(); the test uses it to stop ONE thread (A) exactly
 * there, i.e. it forces the schedule "A is preempted between load and store". Everything else is
 * public API. Assertions state the CORRECT behaviour (contract), so on a buggy build the test FAILS
 * and lists every violation. With -Dr6.control=AB (or BA) thread A is never paused and the two
 * threads' operations run one after the other (sequential controls; must pass on main).
 */
public class TestR6SegmentInfosVersionRace extends LuceneTestCase {

  private static final long TIMEOUT_S = 30;
  private static final long BLOCK_DETECT_MS = 3000;
  /** "" = forced interleaving; "AB" = A fully then B; "BA" = B fully then A (sequential controls). */
  private static final String CONTROL = System.getProperty("r6.control", "");

  static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Store.YES));
    return d;
  }

  static SegmentInfos sis(IndexWriter w) throws Exception {
    Field f = IndexWriter.class.getDeclaredField("segmentInfos");
    f.setAccessible(true);
    return (SegmentInfos) f.get(w);
  }

  /** Read the version the way nrtIsCurrent does: under the IndexWriter monitor. */
  static long versionUnderIWLock(IndexWriter w, SegmentInfos s) {
    synchronized (w) {
      return s.getVersion();
    }
  }

  /** The reader-visible content: the set of live "id" values. */
  static TreeSet<String> liveIds(DirectoryReader r) throws IOException {
    TreeSet<String> ids = new TreeSet<>();
    for (LeafReaderContext ctx : r.leaves()) {
      Bits live = ctx.reader().getLiveDocs();
      StoredFields sf = ctx.reader().storedFields();
      for (int i = 0; i < ctx.reader().maxDoc(); i++) {
        if (live == null || live.get(i)) {
          ids.add(sf.document(i).get("id"));
        }
      }
    }
    return ids;
  }

  /** Directory that pauses thread A once inside copyFrom (addIndexes copying files). */
  static final class PausingDirectory extends FilterDirectory {
    volatile Thread target;
    final CountDownLatch reached = new CountDownLatch(1);
    final CountDownLatch go = new CountDownLatch(1);

    PausingDirectory(Directory in) {
      super(in);
    }

    @Override
    public void copyFrom(Directory from, String src, String dest, IOContext context)
        throws IOException {
      if (target == Thread.currentThread()) {
        target = null;
        reached.countDown();
        try {
          go.await();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      super.copyFrom(from, src, dest, context);
    }
  }

  static String monitorsOf(Thread t) {
    ThreadInfo ti =
        ManagementFactory.getThreadMXBean().getThreadInfo(new long[] {t.threadId()}, true, false)[0];
    StringBuilder sb = new StringBuilder();
    for (MonitorInfo mi : ti.getLockedMonitors()) {
      sb.append(mi.getClassName()).append('@').append(Integer.toHexString(mi.getIdentityHashCode())).append(' ');
    }
    return sb.toString().trim();
  }

  static void checkPausedInNewSegmentName(Thread a, SegmentInfos s, IndexWriter w, String caller) {
    String mons = monitorsOf(a);
    String sisMon = "org.apache.lucene.index.SegmentInfos@" + Integer.toHexString(System.identityHashCode(s));
    String iwMon = "org.apache.lucene.index.IndexWriter@" + Integer.toHexString(System.identityHashCode(w));
    System.out.println("  thread A locked monitors: [" + mons + "]");
    assertTrue("A must hold the SegmentInfos monitor", mons.contains(sisMon));
    assertFalse("A must NOT hold the IndexWriter monitor", mons.contains(iwMon));
    String st = Arrays.toString(R6Hook.pausedAt);
    assertTrue(st, st.contains("IndexWriter.newSegmentName"));
    assertTrue(st, st.contains(caller));
    StringBuilder top = new StringBuilder();
    StackTraceElement[] pa = R6Hook.pausedAt;
    for (int i = 0; i < Math.min(pa.length, 9); i++) {
      top.append("\n      at ").append(pa[i]);
    }
    System.out.println("  thread A paused at:" + top);
  }

  /** Waits briefly; returns null if op is still blocked. */
  static <T> T tryGet(Future<T> f, long ms) throws Exception {
    try {
      return f.get(ms, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      return null;
    }
  }

  /** NRT contract: isCurrent()/openIfChanged()==null must imply "same content as a fresh reader". */
  static void checkFreshness(String label, DirectoryReader old, IndexWriter w, List<String> violations)
      throws IOException {
    boolean cur = old.isCurrent();
    DirectoryReader re1 = DirectoryReader.openIfChanged(old);
    DirectoryReader re2 = DirectoryReader.openIfChanged(old, w);
    DirectoryReader fresh = DirectoryReader.open(w);
    TreeSet<String> o = liveIds(old), f = liveIds(fresh);
    System.out.println("  " + label + ": old.version=" + old.getVersion() + " old ids=" + o
        + " | fresh.version=" + fresh.getVersion() + " fresh ids=" + f + " | old.isCurrent()=" + cur
        + " openIfChanged(old)=" + (re1 == null ? "null" : "reader")
        + " openIfChanged(old,w)=" + (re2 == null ? "null" : "reader"));
    if ((cur || re1 == null || re2 == null) && o.equals(f) == false) {
      violations.add(label + ": STALE reader reported current (isCurrent=" + cur + ", openIfChanged="
          + (re1 == null ? "null" : "reader") + "/" + (re2 == null ? "null" : "reader") + ") but old ids="
          + o + " != fresh ids=" + f + " (old.version=" + old.getVersion() + ", fresh.version="
          + fresh.getVersion() + ")");
    }
    if (re1 != null) re1.close();
    if (re2 != null) re2.close();
    fresh.close();
  }

  /**
   * End-to-end, public API only (except the forced preemption): addIndexes(Directory) in thread A
   * races with deleteDocuments + DirectoryReader.open(writer) in thread B.
   */
  public void testAddIndexesRaceLowersVersionAndHidesChanges() throws Exception {
    List<String> violations = new ArrayList<>();
    ExecutorService ex = Executors.newSingleThreadExecutor();
    ByteBuffersDirectory src = new ByteBuffersDirectory();
    try (IndexWriter sw = new IndexWriter(src, new IndexWriterConfig())) {
      sw.addDocument(doc("s0"));
      sw.commit();
    }
    PausingDirectory dir = new PausingDirectory(new ByteBuffersDirectory());
    IndexWriter w =
        new IndexWriter(
            dir,
            new IndexWriterConfig()
                .setMergePolicy(NoMergePolicy.INSTANCE)
                .setMergeScheduler(new SerialMergeScheduler()));
    w.addDocument(doc("d0"));
    w.addDocument(doc("d1"));
    w.addDocument(doc("keep")); // segment never becomes 100% deleted (dropping it is an extra bump)
    w.commit();
    SegmentInfos s = sis(w);
    final long v0 = versionUnderIWLock(w, s);

    AtomicReference<Throwable> aFailure = new AtomicReference<>();
    Thread a =
        new Thread(
            () -> {
              try {
                w.addIndexes(src);
              } catch (Throwable t) {
                aFailure.set(t);
              }
            },
            "R6-thread-A-addIndexes");
    Callable<DirectoryReader> b1 = () -> {
      w.deleteDocuments(new Term("id", "d0"));
      return DirectoryReader.open(w);
    };
    Callable<DirectoryReader> b2 = () -> {
      w.deleteDocuments(new Term("id", "d1"));
      return DirectoryReader.open(w);
    };
    DirectoryReader r1 = null, r2 = null;
    if (CONTROL.equals("AB")) {
      System.out.println("[addIndexes][CONTROL AB] A runs to completion first, then B; no pause");
      a.start();
      a.join();
      r1 = b1.call();
      r2 = b2.call();
    } else if (CONTROL.equals("BA")) {
      System.out.println("[addIndexes][CONTROL BA] B runs first, then A to completion; no pause");
      r1 = b1.call();
      r2 = b2.call();
      a.start();
      a.join();
    } else {
      R6Hook.arm(a, s, w);
      dir.target = a;
      a.start();
      try {
        assertTrue("A never reached version++ in newSegmentName", R6Hook.loaded.await(TIMEOUT_S, TimeUnit.SECONDS));
        final long v = R6Hook.loadedValue;
        System.out.println("[addIndexes] step 1: A loaded version=" + v + " inside newSegmentName (version before=" + v0 + ")");
        checkPausedInNewSegmentName(a, s, w, "IndexWriter.addIndexes");
        Future<DirectoryReader> f1 = ex.submit(b1);
        r1 = tryGet(f1, BLOCK_DETECT_MS);
        boolean bBlocked = r1 == null;
        long beforeStore = -1;
        if (bBlocked) {
          System.out.println("[addIndexes] step 2: B is BLOCKED while A is inside version++ (writes are mutually exclusive)");
        } else {
          r2 = ex.submit(b2).get(TIMEOUT_S, TimeUnit.SECONDS);
          beforeStore = versionUnderIWLock(w, s);
          System.out.println("[addIndexes] step 2: B did 2 changes while A paused: r1.version=" + r1.getVersion()
              + " r2.version=" + r2.getVersion() + " writer version=" + beforeStore);
        }
        R6Hook.release.countDown(); // A stores loaded+1, then pauses in copyFrom (before publishing)
        assertTrue("A did not reach copyFrom", dir.reached.await(TIMEOUT_S, TimeUnit.SECONDS));
        if (bBlocked) {
          r1 = f1.get(TIMEOUT_S, TimeUnit.SECONDS);
          r2 = ex.submit(b2).get(TIMEOUT_S, TimeUnit.SECONDS);
        }
        long afterStore = versionUnderIWLock(w, s);
        System.out.println("[addIndexes] step 3: A stored its increment; writer version " + beforeStore + " -> " + afterStore);
        if (bBlocked == false && afterStore < beforeStore) {
          violations.add("version DECREASED " + beforeStore + " -> " + afterStore);
        }
        System.out.println("[addIndexes] step 4: A still paused before publishing s0:");
        checkFreshness("r1 during addIndexes", r1, w, violations);
      } finally {
        R6Hook.disarm();
        dir.target = null;
        dir.go.countDown();
        a.join(TimeUnit.SECONDS.toMillis(TIMEOUT_S));
      }
    }
    ex.shutdown();
    assertTrue(ex.awaitTermination(TIMEOUT_S, TimeUnit.SECONDS));
    assertFalse(a.isAlive());
    if (aFailure.get() != null) throw new AssertionError("thread A failed", aFailure.get());
    long finalV = versionUnderIWLock(w, s);
    System.out.println("[addIndexes] step 5: addIndexes returned; writer version=" + finalV + "; increments performed="
        + "4 (A: newSegmentName + checkpoint, B: 2 x delete-apply checkpoint) so expected " + (v0 + 4));
    if (finalV != v0 + 4) {
      violations.add("LOST UPDATE: final version " + finalV + " != " + (v0 + 4));
    }
    System.out.println("[addIndexes] step 6: after addIndexes returned:");
    checkFreshness("r2 after addIndexes", r2, w, violations);
    r1.close();
    r2.close();
    w.close();
    dir.close();
    src.close();
    System.out.println("[addIndexes] VIOLATIONS: " + violations.size());
    for (String x : violations) System.out.println("  - " + x);
    assertTrue("R6 violations: " + violations, violations.isEmpty());
  }

  /**
   * The path in the 2016 ThreadSanitizer report (apache/lucene#8550): an indexing thread creating a
   * new DWPT calls newSegmentName(). Thread B does IndexWriter-locked bumps (here the public
   * setLiveCommitData(..., true), IW:4167-4175).
   */
  public void testDwptCreationRaceLosesIncrements() throws Exception {
    ExecutorService ex = Executors.newSingleThreadExecutor();
    ByteBuffersDirectory dir = new ByteBuffersDirectory();
    IndexWriter w =
        new IndexWriter(
            dir,
            new IndexWriterConfig()
                .setMergePolicy(NoMergePolicy.INSTANCE)
                .setMergeScheduler(new SerialMergeScheduler()));
    w.addDocument(doc("d0"));
    w.commit(); // no DWPT left in the pool: the next addDocument must create one
    SegmentInfos s = sis(w);
    final long v0 = versionUnderIWLock(w, s);
    AtomicReference<Throwable> aFailure = new AtomicReference<>();
    Thread a =
        new Thread(
            () -> {
              try {
                w.addDocument(doc("d1"));
              } catch (Throwable t) {
                aFailure.set(t);
              }
            },
            "R6-thread-A-addDocument");
    Callable<Void> b = () -> {
      w.setLiveCommitData(Map.of("k", "1").entrySet(), true);
      w.setLiveCommitData(Map.of("k", "2").entrySet(), true);
      return null;
    };
    long beforeStore = -1;
    boolean bBlocked = false;
    if (CONTROL.equals("AB")) {
      a.start();
      a.join();
      b.call();
    } else if (CONTROL.equals("BA")) {
      b.call();
      a.start();
      a.join();
    } else {
      R6Hook.arm(a, s, w);
      a.start();
      try {
        assertTrue(R6Hook.loaded.await(TIMEOUT_S, TimeUnit.SECONDS));
        System.out.println("[dwpt] A loaded version=" + R6Hook.loadedValue + " inside newSegmentName");
        checkPausedInNewSegmentName(a, s, w, "DocumentsWriterPerThreadPool.newWriter");
        Future<Void> fb = ex.submit(b);
        bBlocked = tryGet(fb, BLOCK_DETECT_MS) == null && fb.isDone() == false;
        if (bBlocked) {
          // B holds the IndexWriter monitor and waits for the SegmentInfos monitor: do not touch IW here.
          System.out.println("[dwpt] B is BLOCKED while A is inside version++ (writes are mutually exclusive)");
        } else {
          beforeStore = versionUnderIWLock(w, s);
          System.out.println("[dwpt] B completed 2 bumps while A paused; writer version=" + beforeStore);
        }
        R6Hook.release.countDown();
        fb.get(TIMEOUT_S, TimeUnit.SECONDS);
      } finally {
        R6Hook.disarm();
        a.join(TimeUnit.SECONDS.toMillis(TIMEOUT_S));
      }
    }
    ex.shutdown();
    assertTrue(ex.awaitTermination(TIMEOUT_S, TimeUnit.SECONDS));
    assertFalse(a.isAlive());
    if (aFailure.get() != null) throw new AssertionError("thread A failed", aFailure.get());
    long after = versionUnderIWLock(w, s);
    System.out.println("[dwpt] final writer version=" + after + " (3 increments after " + v0 + " => expected " + (v0 + 3) + ")");
    w.close();
    dir.close();
    assertTrue("version " + (bBlocked ? "" : "went " + beforeStore + " -> " + after + "; ") + "final " + after
        + " expected " + (v0 + 3), after == v0 + 3);
  }
}
