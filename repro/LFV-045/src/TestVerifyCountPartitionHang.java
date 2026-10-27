import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.ExitableDirectoryReader;
import org.apache.lucene.index.ExitableDirectoryReader.ExitingReaderException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.QueryTimeout;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.ConstantScoreWeight;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TotalHitCountCollectorManager;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent verification of the candidate "IndexSearcher#count hangs forever when
 * Weight#count throws on a partitioned segment".
 *
 * <p>Synchronisation is done ONLY through public extension points: a custom {@link Executor}
 * passed to the {@link IndexSearcher} constructor, and the body of {@link Weight#count} / {@link
 * QueryTimeout#shouldExit} (user code). IndexSearcher#search(LeafReaderContextPartition[],...) is
 * NOT overridden. The "other partition is now inside CompletableFuture.get" condition is detected
 * from thread stack traces, so the interleaving is forced, not timed. A wall-clock bound is only
 * used to turn "never returns" into a test failure.
 *
 * <p>Tests whose names start with testExpect* assert the behaviour a user would expect (the
 * exception propagates promptly). They FAIL when the bug is present.
 */
@ThreadLeakScope(ThreadLeakScope.Scope.NONE) // hung threads are reported by the tests themselves
public class TestVerifyCountPartitionHang extends LuceneTestCase {

  static final String PARTITION_COLLECTOR =
      "org.apache.lucene.search.TotalHitCountCollectorManager$LeafPartitionAwareTotalHitCountCollector";
  static final long HANG_BOUND_SECONDS = 10;

  // ------------------------------------------------------------------ fixtures

  /** One segment, {@code numDocs} docs, all with f:a. */
  static Directory oneSegmentIndex(int numDocs) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < numDocs; i++) {
        Document d = new Document();
        d.add(new StringField("f", "a", Field.Store.NO));
        w.addDocument(d);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  /** Searcher whose slices come from the public static IndexSearcher#slices(...) helper. */
  static IndexSearcher searcher(
      IndexReader r, Executor ex, int maxDocsPerSlice, boolean allowPartitions) {
    IndexSearcher s =
        new IndexSearcher(r, ex) {
          @Override
          protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
            return slices(leaves, maxDocsPerSlice, 1, allowPartitions);
          }
        };
    s.setQueryCache(null); // keep the weight chain minimal; see testExpectTermQueryTimeout... for default-ish setup
    return s;
  }

  /** A query whose Weight#count runs a hook and then fails like a disk read error would. */
  static final class CountFailsQuery extends Query {
    final Runnable hook;
    final AtomicInteger countCalls = new AtomicInteger();

    CountFailsQuery(Runnable hook) {
      this.hook = hook;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) {
      return new ConstantScoreWeight(this, boost) {
        @Override
        public int count(LeafReaderContext context) throws IOException {
          countCalls.incrementAndGet();
          hook.run();
          throw new IOException("verify: simulated read error in Weight#count");
        }

        @Override
        public ScorerSupplier scorerSupplier(LeafReaderContext context) {
          throw new AssertionError("not reached: count() never returns -1");
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
          return false;
        }
      };
    }

    @Override
    public String toString(String field) {
      return "CountFailsQuery";
    }

    @Override
    public void visit(QueryVisitor visitor) {}

    @Override
    public boolean equals(Object o) {
      return this == o;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  // ------------------------------------------------------------------ stack-based sync helpers

  static boolean isPartitionWaiter(StackTraceElement[] st) {
    boolean inGetLeafCollector = false;
    boolean inFutureGet = false;
    for (StackTraceElement f : st) {
      if (f.getClassName().equals(PARTITION_COLLECTOR)
          && f.getMethodName().equals("getLeafCollector")) {
        inGetLeafCollector = true;
      }
      if (f.getClassName().equals("java.util.concurrent.CompletableFuture")
          && f.getMethodName().equals("get")) {
        inFutureGet = true;
      }
    }
    return inGetLeafCollector && inFutureGet;
  }

  /** A thread (other than self) whose stack is inside the partition collector's future.get(). */
  static Thread findPartitionWaiter(Thread self) {
    for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
      if (e.getKey() != self && isPartitionWaiter(e.getValue())) {
        return e.getKey();
      }
    }
    return null;
  }

  static Thread awaitPartitionWaiter(Thread self) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Thread t = findPartitionWaiter(self);
      if (t != null) {
        return t;
      }
      Thread.onSpinWait();
      try {
        Thread.sleep(1);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new AssertionError(ie);
      }
    }
    throw new AssertionError("second partition never reached CompletableFuture.get");
  }

  static String stack(Thread t) {
    StringBuilder sb = new StringBuilder();
    sb.append('"').append(t.getName()).append("\" state=").append(t.getState()).append('\n');
    StackTraceElement[] st = t.getStackTrace();
    for (int i = 0; i < Math.min(st.length, 14); i++) {
      sb.append("    at ").append(st[i]).append('\n');
    }
    return sb.toString();
  }

  /** Runs {@code body} on a daemon "driver" thread and waits at most HANG_BOUND_SECONDS. */
  static final class Driver {
    final FutureTask<Integer> task;
    final Thread thread;

    Driver(java.util.concurrent.Callable<Integer> body) {
      task = new FutureTask<>(body);
      thread = new Thread(task, "verify-driver");
      thread.setDaemon(true);
      thread.start();
    }

    /** Returns the thrown exception, or null if it returned normally; throws TimeoutException. */
    Throwable await() throws InterruptedException, TimeoutException {
      try {
        Integer c = task.get(HANG_BOUND_SECONDS, TimeUnit.SECONDS);
        System.out.println("  driver returned count=" + c);
        return null;
      } catch (ExecutionException e) {
        return e.getCause();
      }
    }
  }

  /**
   * Executor for the "waiter on the caller thread" interleaving: execute(r) starts r on a fresh
   * daemon thread and does not return until the gate opens (the forked task is the first arriver
   * and opens the gate from inside Weight#count).
   */
  static final class ForkThenBlockCallerExecutor implements Executor {
    final CountDownLatch gate;
    final List<Thread> forked = new ArrayList<>();

    ForkThenBlockCallerExecutor(CountDownLatch gate) {
      this.gate = gate;
    }

    @Override
    public void execute(Runnable r) {
      Thread t = new Thread(r, "verify-forked-" + forked.size());
      t.setDaemon(true);
      forked.add(t);
      t.start();
      try {
        if (gate.await(30, TimeUnit.SECONDS) == false) {
          throw new AssertionError("gate never opened");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }
  }

  /**
   * Executor for the "waiter on a pool thread" interleaving: execute(r) returns at once; the forked
   * thread only starts running r after the gate opens (the caller ran task 0 inline, is the first
   * arriver and opens the gate from inside Weight#count / QueryTimeout#shouldExit).
   */
  static final class DelayedForkExecutor implements Executor {
    final CountDownLatch gate;
    final List<Thread> forked = new ArrayList<>();

    DelayedForkExecutor(CountDownLatch gate) {
      this.gate = gate;
    }

    @Override
    public void execute(Runnable r) {
      Thread t =
          new Thread(
              () -> {
                try {
                  if (gate.await(30, TimeUnit.SECONDS) == false) {
                    throw new AssertionError("gate never opened");
                  }
                } catch (InterruptedException e) {
                  throw new AssertionError(e);
                }
                r.run();
              },
              "verify-forked-" + forked.size());
      t.setDaemon(true);
      forked.add(t);
      t.start();
    }
  }

  /** Hook used by the first arriver: open the gate, then wait until the other partition waits. */
  static Runnable firstArriverHook(CountDownLatch gate, AtomicBoolean fired) {
    return () -> {
      if (fired.compareAndSet(false, true)) {
        gate.countDown();
        Thread waiter = awaitPartitionWaiter(Thread.currentThread());
        System.out.println(
            "  first arriver ["
                + Thread.currentThread().getName()
                + "] now fails; other partition is parked: "
                + waiter.getName());
      }
    };
  }

  static void reportHang(String label, Driver d, List<Thread> forked) {
    System.out.println("  HANG (" + label + "): no return after " + HANG_BOUND_SECONDS + " s");
    System.out.println("  " + stack(d.thread).replace("\n", "\n  "));
    for (Thread t : forked) {
      System.out.println("  " + stack(t).replace("\n", "\n  "));
    }
  }

  // ------------------------------------------------------------------ controls (expected to pass)

  /** Control 1: same failing query, no segment partitions -> IOException propagates. */
  public void testControlNoPartitions() throws Exception {
    try (Directory dir = oneSegmentIndex(10);
        IndexReader r = DirectoryReader.open(dir)) {
      ExecutorService pool = Executors.newFixedThreadPool(2);
      try {
        IndexSearcher s = searcher(r, pool, 5, false);
        assertEquals(1, s.getSlices().length);
        CountFailsQuery q = new CountFailsQuery(() -> {});
        Driver d = new Driver(() -> s.count(q));
        Throwable t = d.await();
        assertTrue("expected IOException, got " + t, t instanceof IOException);
        System.out.println("  control (no partitions): " + t);
      } finally {
        pool.shutdown();
      }
    }
  }

  /**
   * Control 2: partitions, but the executor runs tasks sequentially (Runnable::run) -> the failing
   * first partition cancels the not-yet-started second one; IOException propagates.
   */
  public void testControlPartitionsSequentialExecutor() throws Exception {
    try (Directory dir = oneSegmentIndex(10);
        IndexReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = searcher(r, Runnable::run, 5, true);
      assertEquals(2, s.getSlices().length);
      CountFailsQuery q = new CountFailsQuery(() -> {});
      Driver d = new Driver(() -> s.count(q));
      Throwable t = d.await();
      assertTrue("expected IOException, got " + t, t instanceof IOException);
      System.out.println("  control (partitions, sequential executor): " + t);
    }
  }

  // ------------------------------------------------------------------ bug checks (fail if buggy)

  /** Interleaving A: forked thread is first arriver and throws; caller thread parks forever. */
  public void testExpectIOExceptionWaiterOnCaller() throws Exception {
    try (Directory dir = oneSegmentIndex(10);
        IndexReader r = DirectoryReader.open(dir)) {
      CountDownLatch gate = new CountDownLatch(1);
      ForkThenBlockCallerExecutor ex = new ForkThenBlockCallerExecutor(gate);
      IndexSearcher s = searcher(r, ex, 5, true);
      assertEquals(2, s.getSlices().length);
      CountFailsQuery q = new CountFailsQuery(firstArriverHook(gate, new AtomicBoolean()));
      Driver d = new Driver(() -> s.count(q));
      Throwable t;
      try {
        t = d.await();
      } catch (TimeoutException te) {
        reportHang("waiter on caller", d, ex.forked);
        for (Thread f : ex.forked) {
          System.out.println("  forked thread alive after it threw? " + f.isAlive());
        }
        System.out.println("  Weight#count calls: " + q.countCalls.get());
        d.thread.interrupt(); // free the parked thread so the JVM state is clean
        fail("IndexSearcher#count did not return within " + HANG_BOUND_SECONDS + " s");
        return;
      }
      assertTrue("expected IOException, got " + t, t instanceof IOException);
    }
  }

  /** Interleaving B: caller is first arriver and throws; pool thread parks; caller in collectResults. */
  public void testExpectIOExceptionWaiterOnPoolThread() throws Exception {
    try (Directory dir = oneSegmentIndex(10);
        IndexReader r = DirectoryReader.open(dir)) {
      CountDownLatch gate = new CountDownLatch(1);
      DelayedForkExecutor ex = new DelayedForkExecutor(gate);
      IndexSearcher s = searcher(r, ex, 5, true);
      CountFailsQuery q = new CountFailsQuery(firstArriverHook(gate, new AtomicBoolean()));
      Driver d = new Driver(() -> s.count(q));
      Throwable t;
      try {
        t = d.await();
      } catch (TimeoutException te) {
        reportHang("waiter on pool thread", d, ex.forked);
        System.out.println("  Weight#count calls: " + q.countCalls.get());
        for (Thread f : ex.forked) {
          f.interrupt();
        }
        d.thread.interrupt();
        fail("IndexSearcher#count did not return within " + HANG_BOUND_SECONDS + " s");
        return;
      }
      assertTrue("expected IOException, got " + t, t instanceof IOException);
    }
  }

  /**
   * Stock-component trigger: plain TermQuery on an ExitableDirectoryReader whose QueryTimeout fires
   * while the first partition's TermWeight#count opens the terms enum. Expected: the
   * ExitingReaderException (a timeout) propagates.
   */
  public void testExpectTermQueryTimeoutPropagates() throws Exception {
    try (Directory dir = oneSegmentIndex(10);
        DirectoryReader raw = DirectoryReader.open(dir)) {
      CountDownLatch gate = new CountDownLatch(1);
      AtomicBoolean fired = new AtomicBoolean();
      Runnable hook = firstArriverHook(gate, fired);
      QueryTimeout timeout =
          new QueryTimeout() {
            @Override
            public boolean shouldExit() {
              for (StackTraceElement f : Thread.currentThread().getStackTrace()) {
                if (f.getClassName().equals("org.apache.lucene.search.TermQuery$TermWeight")
                    && f.getMethodName().equals("count")) {
                  hook.run(); // only the first time: wait until the other partition parks
                  return true; // the time budget is exhausted now
                }
              }
              return false;
            }
          };
      DirectoryReader r = new ExitableDirectoryReader(raw, timeout);
      DelayedForkExecutor ex = new DelayedForkExecutor(gate);
      IndexSearcher s =
          new IndexSearcher(r, ex) {
            @Override
            protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
              return slices(leaves, 5, 1, true);
            }
          }; // default LRUQueryCache + caching policy left in place
      Driver d = new Driver(() -> s.count(new TermQuery(new Term("f", "a"))));
      Throwable t;
      try {
        t = d.await();
      } catch (TimeoutException te) {
        reportHang("TermQuery + ExitableDirectoryReader timeout", d, ex.forked);
        for (Thread f : ex.forked) {
          f.interrupt();
        }
        d.thread.interrupt();
        fail("timed-out IndexSearcher#count did not return within " + HANG_BOUND_SECONDS + " s");
        return;
      }
      assertTrue("expected ExitingReaderException, got " + t, t instanceof ExitingReaderException);
    }
  }

  /**
   * Secondary (manager reuse, NO concurrency): after a failed search the per-leaf future stays in
   * the manager's map (reduce() is not called on failure), so the NEXT search with the same
   * manager instance parks forever even with a sequential executor and a query that does not fail.
   */
  public void testExpectReusedManagerAfterFailureStillWorks() throws Exception {
    try (Directory dir = oneSegmentIndex(10);
        IndexReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = searcher(r, Runnable::run, 5, true);
      TotalHitCountCollectorManager mgr = new TotalHitCountCollectorManager(s.getSlices());
      expectThrows(IOException.class, () -> s.search(new CountFailsQuery(() -> {}), mgr));
      Driver d = new Driver(() -> s.search(new TermQuery(new Term("f", "a")), mgr));
      Throwable t;
      try {
        t = d.await();
      } catch (TimeoutException te) {
        reportHang("reused manager, sequential executor", d, List.of());
        d.thread.interrupt();
        fail("second search with the reused manager did not return");
        return;
      }
      assertNull("unexpected " + t, t);
    }
  }

  // ------------------------------------------------------------------ hook-free reachability

  /**
   * No test hooks at all: a real fixed thread pool, an ExitableDirectoryReader whose (stock)
   * QueryTimeout has already expired when count() runs, a plain TermQuery, a 50-doc segment split
   * into 5 partitions. Counts how many of N calls hang (bounded wait). Informational: it asserts
   * nothing about the rate, only prints it.
   */
  public void testHookFreeHangRate() throws Exception {
    int trials = Integer.getInteger("verify.trials", 200);
    long boundMs = 1000;
    try (Directory dir = oneSegmentIndex(50);
        DirectoryReader raw = DirectoryReader.open(dir)) {
      QueryTimeout expired = () -> true; // budget already spent (e.g. by earlier request phases)
      DirectoryReader r = new ExitableDirectoryReader(raw, expired);
      int hangs = 0, hangsParkedAt131 = 0, exits = 0, other = 0;
      for (int i = 0; i < trials; i++) {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
          IndexSearcher s =
              new IndexSearcher(r, pool) {
                @Override
                protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
                  return slices(leaves, 10, 1, true);
                }
              };
          if (i == 0) {
            System.out.println("  slices=" + s.getSlices().length + " (one 50-doc segment)");
          }
          FutureTask<Integer> f = new FutureTask<>(() -> s.count(new TermQuery(new Term("f", "a"))));
          Thread drv = new Thread(f, "verify-hookfree-driver-" + i);
          drv.setDaemon(true);
          drv.start();
          try {
            f.get(boundMs, TimeUnit.MILLISECONDS);
            other++;
          } catch (ExecutionException e) {
            if (e.getCause() instanceof ExitingReaderException) {
              exits++;
            } else {
              other++;
              System.out.println("  unexpected: " + e.getCause());
            }
          } catch (TimeoutException te) {
            hangs++;
            int parked = 0;
            for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
              if (e.getKey().getName().startsWith("pool-") && isPartitionWaiter(e.getValue())) {
                parked++;
              }
            }
            if (parked > 0) {
              hangsParkedAt131++;
            }
            if (hangs == 1) {
              System.out.println("  first hook-free hang at trial " + i + ":");
              System.out.println("  " + stack(drv).replace("\n", "\n  "));
              for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
                if (isPartitionWaiter(e.getValue())) {
                  System.out.println("  " + stack(e.getKey()).replace("\n", "\n  "));
                }
              }
            }
            drv.interrupt();
          }
        } finally {
          pool.shutdownNow(); // interrupts parked pool threads
          pool.awaitTermination(5, TimeUnit.SECONDS);
        }
      }
      System.out.println(
          "  hook-free: trials="
              + trials
              + " hangs(>"
              + boundMs
              + "ms)="
              + hangs
              + " (of which with a pool thread parked in the partition collector's future.get: "
              + hangsParkedAt131
              + ") ExitingReaderException="
              + exits
              + " other="
              + other);
    }
  }
}
