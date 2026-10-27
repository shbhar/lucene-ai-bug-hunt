/*
 * Independent verification of candidate F3: "MMapDirectory shared-arena grouping lost:
 * onClose removes a newer live arena's map entry (remove by key)".
 *
 * Written from scratch for this verification (the candidate's repros were only read to learn the
 * claimed sequence). Lives in org.apache.lucene.store so it can read the package-private
 * MMapDirectory.arenas map and MemorySegmentIndexInput.arena field (the same access Lucene's own
 * TestMMapDirectory uses). No Lucene class is modified, wrapped or replaced, and no reflection is
 * used.
 *
 * Each test asserts the behaviour the grouping design intends (a live group arena that still has
 * permits stays registered and is shared by later opens of the same group), and prints what it saw.
 */
package org.apache.lucene.store;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestVerifyArenaGroupingLost extends LuceneTestCase {

  /** Effective permits per group arena; same sysprop/default logic as MMapDirectory. */
  static final int PERMITS =
      Integer.getInteger(
          MMapDirectory.SHARED_ARENA_MAX_PERMITS_SYSPROP, RefCountedSharedArena.DEFAULT_MAX_PERMITS);

  static void log(String s) {
    System.out.println("[verify] " + s);
  }

  static RefCountedSharedArena arenaOf(IndexInput in) {
    Arena a = ((MemorySegmentIndexInput) in).arena;
    assertTrue("expected a grouped arena, got " + a, a instanceof RefCountedSharedArena);
    return (RefCountedSharedArena) a;
  }

  static String id(Object o) {
    return o == null ? "null" : "A@" + Integer.toHexString(System.identityHashCode(o));
  }

  static void writeFile(Directory dir, String name, long... longs) throws IOException {
    try (IndexOutput out = dir.createOutput(name, IOContext.DEFAULT)) {
      if (longs.length == 0) {
        out.writeLong(42L);
      }
      for (long l : longs) {
        out.writeLong(l); // little-endian since Lucene 9
      }
    }
  }

  /**
   * Trigger 1 (single thread, no timing): permits of arena A1 run out while A1 is still alive, so
   * a second arena A2 is installed for the same group; when A1 later closes, its onClose removes
   * the key, i.e. A2's entry.
   */
  public void testPermitExhaustionDropsLiveArenaEntry() throws Exception {
    log("PERMITS=" + PERMITS);
    try (MMapDirectory dir = new MMapDirectory(createTempDir("permits"))) {
      for (int i = 0; i < PERMITS + 3; i++) {
        writeFile(dir, "_0.f" + i);
      }
      IndexInput keep = dir.openInput("_0.f0", IOContext.DEFAULT);
      RefCountedSharedArena a1 = arenaOf(keep);
      assertSame(a1, dir.arenas.get("0"));
      // use the remaining PERMITS-1 permits of A1 while 'keep' holds A1 alive
      for (int i = 1; i < PERMITS; i++) {
        try (IndexInput in = dir.openInput("_0.f" + i, IOContext.DEFAULT)) {
          assertSame("open #" + i + " should share A1", a1, arenaOf(in));
        }
      }
      IndexInput second = dir.openInput("_0.f" + PERMITS, IOContext.DEFAULT);
      RefCountedSharedArena a2 = arenaOf(second);
      assertNotSame("A1 is exhausted, so a new arena is expected", a1, a2);
      assertSame("A2 must be registered", a2, dir.arenas.get("0"));
      log("step: A1=" + id(a1) + " exhausted, A2=" + id(a2) + " registered: map[0]=" + id(dir.arenas.get("0")));

      keep.close(); // A1 refcount 1 -> 0: A1 closes and runs its onClose
      assertFalse(a1.scope().isAlive());
      assertTrue("A2 still alive", a2.scope().isAlive());
      RefCountedSharedArena afterClose = dir.arenas.get("0");
      log("after A1 closed: map[0]=" + id(afterClose) + " (A2 alive=" + a2.scope().isAlive() + ")");

      IndexInput third = dir.openInput("_0.f" + (PERMITS + 1), IOContext.DEFAULT);
      RefCountedSharedArena a3 = arenaOf(third);
      log("next open of a _0 file got " + id(a3) + (a3 == a2 ? " (shares A2)" : " (NEW arena, A2 not shared)"));
      try {
        assertSame("live A2 (permits left) lost its map entry when A1 closed", a2, afterClose);
        assertSame("next open should share the live A2", a2, a3);
      } finally {
        second.close();
        third.close();
      }
      assertEquals(0, dir.arenas.size());
    }
  }

  /**
   * Same trigger, steady state: files of one group are opened in a sliding window of 2 (open file
   * i, then close file i-1). That is the access pattern of per-generation doc-values files under
   * NRT reopen. With intact grouping each arena serves PERMITS files; count how many arenas are
   * actually used.
   */
  public void testSlidingWindowCollapsesToOneArenaPerFile() throws Exception {
    final int n = 10 * PERMITS;
    try (MMapDirectory dir = new MMapDirectory(createTempDir("window"))) {
      for (int i = 0; i < n; i++) {
        writeFile(dir, "_0.w" + i);
      }
      Set<RefCountedSharedArena> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
      ArrayDeque<IndexInput> window = new ArrayDeque<>();
      for (int i = 0; i < n; i++) {
        IndexInput in = dir.openInput("_0.w" + i, IOContext.DEFAULT);
        distinct.add(arenaOf(in));
        window.addLast(in);
        if (window.size() > 1) {
          window.removeFirst().close();
        }
      }
      while (window.isEmpty() == false) {
        window.removeFirst().close();
      }
      int expected = (n + PERMITS - 1) / PERMITS;
      log("sliding window: files=" + n + " distinct arenas used=" + distinct.size() + " (intact grouping would use " + expected + ", NO_GROUPING would use " + n + ")");
      assertEquals(0, dir.arenas.size());
      assertEquals("arenas used for " + n + " files of one group", expected, distinct.size());
    }
  }

  /**
   * Trigger 2 (thread race, deterministic without hooks): the retry loop of
   * MemorySegmentIndexInput.close() calls release() -> onClose -> arenas.remove(key) once per
   * failed Arena.close() attempt. A real FFM downcall (nanosleep) holding a segment of A1 keeps
   * A1's scope acquired for ~1.5 s, so the closing thread keeps re-running onClose and removes any
   * newer arena of the same group that another thread installs meanwhile.
   */
  public void testCloseRetryLoopDropsNewerArena() throws Exception {
    try (MMapDirectory dir = new MMapDirectory(createTempDir("retry"))) {
      writeFile(dir, "_0.ts", 1L, 500_000_000L); // struct timespec {tv_sec=1, tv_nsec=5e8}
      writeFile(dir, "_0.y");
      writeFile(dir, "_0.z");
      writeFile(dir, "_0.w");
      IndexInput x = dir.openInput("_0.ts", IOContext.DEFAULT);
      RefCountedSharedArena a1 = arenaOf(x);
      MemorySegment ts = ((MemorySegmentAccessInput) x).segmentSliceOrNull(0, 16);
      assertNotNull(ts);

      Linker linker = Linker.nativeLinker();
      MethodHandle nanosleep =
          linker.downcallHandle(
              linker.defaultLookup().find("nanosleep").orElseThrow(),
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
      CountDownLatch aboutToCall = new CountDownLatch(1);
      AtomicReference<Throwable> err = new AtomicReference<>();
      Thread user =
          new Thread(
              () -> {
                try {
                  aboutToCall.countDown();
                  int rc = (int) nanosleep.invokeExact(ts, MemorySegment.NULL);
                  assertEquals(0, rc);
                } catch (Throwable t) {
                  err.set(t);
                }
              },
              "segment-user");
      user.start();
      aboutToCall.await();
      Thread.sleep(200); // let the downcall start (it holds A1's scope for 1.5 s)

      Thread closer =
          new Thread(
              () -> {
                try {
                  x.close();
                } catch (Throwable t) {
                  err.set(t);
                }
              },
              "closer");
      closer.start();
      // wait until the closer has done its CAS to CLOSED and run onClose at least once
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
      while (dir.arenas.get("0") != null && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }
      assertNull("closer removed A1's entry", dir.arenas.get("0"));
      assertTrue("A1 still alive: Arena.close() is failing and being retried", a1.scope().isAlive());
      assertTrue(closer.isAlive());

      IndexInput y = dir.openInput("_0.y", IOContext.DEFAULT);
      RefCountedSharedArena a2 = arenaOf(y);
      assertNotSame(a1, a2);
      Thread.sleep(50);
      RefCountedSharedArena mapAfter = dir.arenas.get("0");
      IndexInput z = dir.openInput("_0.z", IOContext.DEFAULT);
      RefCountedSharedArena az = arenaOf(z);
      boolean closerStillRetrying = closer.isAlive() && a1.scope().isAlive();
      log("during retry loop: A1=" + id(a1) + " closerRetrying=" + closerStillRetrying + " y->" + id(a2) + " map[0] 50ms later=" + id(mapAfter) + " z->" + id(az));

      user.join();
      closer.join();
      assertNull(err.get());
      assertFalse(a1.scope().isAlive());
      RefCountedSharedArena mapAtEnd = dir.arenas.get("0");
      IndexInput w = dir.openInput("_0.w", IOContext.DEFAULT);
      RefCountedSharedArena aw = arenaOf(w);
      log("after A1 closed: map[0]=" + id(mapAtEnd) + " w->" + id(aw) + "; y's arena alive=" + a2.scope().isAlive());
      try {
        assertTrue("precondition: closer was still retrying when z was opened", closerStillRetrying);
        assertSame("A2 (live, permits left) should stay registered", a2, mapAfter);
        assertSame("z should share A2", a2, az);
        assertSame("w should share A2", a2, aw);
      } finally {
        y.close();
        z.close();
        w.close();
      }
      assertEquals(0, dir.arenas.size());
    }
  }

  /** permits left, parsed from RefCountedSharedArena.toString() ("value=" high 16 bits). */
  static int permitsLeft(RefCountedSharedArena a) {
    String s = a.toString();
    int v = Integer.parseInt(s.substring(s.indexOf("value=") + 6, s.indexOf(", arena=")));
    return v >>> 16;
  }

  /**
   * End-to-end with the public IndexWriter/DirectoryReader API: one segment, repeated numeric DV
   * updates, NRT reopen, close the old reader. Each reopen opens new generation DV files of group
   * "0-g". A FilterDirectory records which arena each opened input got (read-only observation).
   * After every reopen: if the arena used by the newest 0-g file is alive and still has permits,
   * it must be the registered arena of group 0-g. Also reports how many arenas were used in total.
   */
  public void testNrtDocValuesUpdatesUnregisterLiveArena() throws Exception {
    final int reopens = 4 * PERMITS;
    try (MMapDirectory mmap = new MMapDirectory(createTempDir("nrt"))) {
      Set<RefCountedSharedArena> used = Collections.newSetFromMap(new IdentityHashMap<>());
      AtomicReference<RefCountedSharedArena> newest = new AtomicReference<>();
      int[] opens = new int[1];
      Directory dir =
          new FilterDirectory(mmap) {
            @Override
            public IndexInput openInput(String name, IOContext context) throws IOException {
              IndexInput in = super.openInput(name, context);
              if (in instanceof MemorySegmentIndexInput m
                  && m.arena instanceof RefCountedSharedArena r
                  && "0-g".equals(MMapDirectory.GROUP_BY_SEGMENT.apply(name).orElse(""))) {
                used.add(r);
                newest.set(r);
                opens[0]++;
              }
              return in;
            }
          };
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setMergePolicy(NoMergePolicy.INSTANCE)
              .setCodec(org.apache.lucene.tests.util.TestUtil.getDefaultCodec());
      int violations = 0;
      int firstViolation = -1;
      String firstViolationState = null;
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 10; i++) {
          Document d = new Document();
          d.add(new StringField("id", "" + i, Field.Store.NO));
          d.add(new NumericDocValuesField("dv", 0));
          w.addDocument(d);
        }
        w.commit();
        DirectoryReader r = DirectoryReader.open(w);
        for (int i = 1; i <= reopens; i++) {
          w.updateNumericDocValue(new Term("id", "3"), "dv", i);
          DirectoryReader nr = DirectoryReader.openIfChanged(r, w);
          assertNotNull(nr);
          r.close();
          r = nr;
          RefCountedSharedArena last = newest.get();
          RefCountedSharedArena registered = mmap.arenas.get("0-g");
          if (last != null && last.scope().isAlive() && permitsLeft(last) > 0 && registered != last) {
            if (violations++ == 0) {
              firstViolation = i;
              firstViolationState = "newest=" + last + " registered=" + registered;
            }
          }
        }
        r.close();
      }
      log("NRT DV updates: reopens=" + reopens + " opens(0-g)=" + opens[0] + " arenas used(0-g)=" + used.size()
          + " reopens where a live arena with permits left was unregistered=" + violations
          + (firstViolation > 0 ? " (first at reopen " + firstViolation + ": " + firstViolationState + ")" : ""));
      assertEquals("reopens where the live 0-g arena (permits left) was not registered", 0, violations);
    }
  }
}
