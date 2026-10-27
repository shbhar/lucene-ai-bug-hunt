import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Scorable;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TaskExecutor;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent verification of: "TaskExecutor#invokeAll returns while a task is still
 * running when the calling thread is interrupted".
 *
 * <p>Determinism: the executor below starts one fresh thread per execute() call and does not return
 * from execute() until the task picked up by that thread has signalled that it started. Hence the
 * executor thread always claims task 0 and the calling thread always runs task 1 (the last one),
 * then enters collectResults and waits on future 0. No sleeps are used to order events, except in
 * test 3, which polls the caller's thread state to deliver an asynchronous interrupt.
 *
 * <p>Every test asserts the contract the code itself states (javadoc "wait for them to complete",
 * comment "invokeAll does not leave any running tasks behind when it returns", assertion "Some
 * tasks are still running?"). A failure means the contract was violated on the jar under test.
 */
public class VerifyInvokeAllInterrupt extends LuceneTestCase {

  static final String WORKER = "verify-worker";

  /** Starts a new thread per task, and blocks execute() until that thread's task has started. */
  static final class HandOffExecutor implements Executor {
    final CountDownLatch started = new CountDownLatch(1);
    final List<Thread> threads = new CopyOnWriteArrayList<>();

    @Override
    public void execute(Runnable r) {
      Thread t = new Thread(r, WORKER);
      t.setDaemon(true);
      threads.add(t);
      t.start();
      awaitUninterruptibly(started);
    }

    void joinAll() throws InterruptedException {
      for (Thread t : threads) {
        t.join(TimeUnit.SECONDS.toMillis(10));
        assertFalse("worker thread did not finish", t.isAlive());
      }
    }
  }

  static void awaitUninterruptibly(CountDownLatch l) {
    boolean interrupted = false;
    while (true) {
      try {
        if (l.await(30, TimeUnit.SECONDS) == false) {
          throw new AssertionError("latch timeout");
        }
        break;
      } catch (InterruptedException e) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  static boolean onWorker() {
    return Thread.currentThread().getName().equals(WORKER);
  }

  static String describe(Throwable t) {
    if (t == null) {
      return "returned normally";
    }
    StringBuilder sb = new StringBuilder(t.getClass().getName() + ": " + t.getMessage());
    for (Throwable s : t.getSuppressed()) {
      sb.append(" [suppressed ").append(s).append(']');
    }
    for (StackTraceElement e : t.getStackTrace()) {
      if (e.getClassName().startsWith("org.apache.lucene.search.")) {
        sb.append(" at ").append(e);
        break;
      }
    }
    return sb.toString();
  }

  static boolean contains(Throwable t, Throwable target) {
    if (t == null) {
      return false;
    }
    if (t == target) {
      return true;
    }
    for (Throwable s : t.getSuppressed()) {
      if (contains(s, target)) {
        return true;
      }
    }
    return contains(t.getCause(), target);
  }

  static boolean parkedInCollectResults(Thread t) {
    boolean inCollect = false, inAwait = false;
    for (StackTraceElement e : t.getStackTrace()) {
      inAwait |= e.getClassName().equals("java.util.concurrent.FutureTask")
          && e.getMethodName().equals("awaitDone");
      inCollect |= e.getClassName().equals("org.apache.lucene.search.TaskExecutor")
          && e.getMethodName().equals("collectResults");
    }
    return inAwait && inCollect && t.getState() == Thread.State.WAITING;
  }

  /**
   * Releases task 0 only once the caller is parked in collectResults again (which a correct
   * invokeAll does after the interrupt, because it keeps waiting). The buggy invokeAll never parks
   * in these tests: Future#get throws at once because the interrupt flag is already set, so it
   * returns before task 0 is released. Lets the same test pass on a fixed TaskExecutor instead of
   * deadlocking.
   */
  static Thread releaseWhenParked(Thread caller, CountDownLatch release, AtomicBoolean callerDone) {
    Thread h =
        new Thread(
            () -> {
              while (callerDone.get() == false) {
                if (parkedInCollectResults(caller)) {
                  release.countDown();
                  return;
                }
                try {
                  Thread.sleep(2);
                } catch (InterruptedException e) {
                  return;
                }
              }
            },
            "verify-releaser");
    h.setDaemon(true);
    h.start();
    return h;
  }

  /** The two callables: task 0 runs on the worker and blocks; task 1 runs on the caller. */
  static final class Scenario {
    final HandOffExecutor executor = new HandOffExecutor();
    final CountDownLatch release = new CountDownLatch(1);
    final AtomicBoolean task0Finished = new AtomicBoolean();
    final RuntimeException late = new RuntimeException("task 0 failed after being released");
    final List<Callable<Integer>> callables = new ArrayList<>();

    Scenario(boolean task0ThrowsLate, boolean task1InterruptsCaller) {
      callables.add(
          () -> {
            assertTrue("task 0 must run on the executor thread", onWorker());
            executor.started.countDown();
            awaitUninterruptibly(release);
            task0Finished.set(true);
            if (task0ThrowsLate) {
              throw late;
            }
            return 0;
          });
      callables.add(
          () -> {
            assertFalse("task 1 must run on the calling thread", onWorker());
            if (task1InterruptsCaller) {
              // e.g. a request-cancellation interrupt that arrives while the caller is busy
              // running its own share of the work
              Thread.currentThread().interrupt();
            }
            return 1;
          });
    }
  }

  /**
   * Test 1: the calling thread gets interrupted while it runs its own task. Fully deterministic.
   */
  public void testInterruptDuringCallerTaskLeavesTaskRunning() throws Exception {
    Scenario s = new Scenario(false, true);
    TaskExecutor te = new TaskExecutor(s.executor);
    Throwable thrown = null;
    boolean task0RunningAtReturn;
    AtomicBoolean done = new AtomicBoolean();
    Thread releaser = releaseWhenParked(Thread.currentThread(), s.release, done);
    try {
      te.invokeAll(s.callables);
    } catch (Throwable t) {
      thrown = t;
    } finally {
      task0RunningAtReturn = s.task0Finished.get() == false;
      done.set(true);
      s.release.countDown();
      releaser.join();
      s.executor.joinAll();
    }
    System.out.println(
        "[test1] invokeAll outcome: "
            + describe(thrown)
            + " | task 0 still running when invokeAll ended: "
            + task0RunningAtReturn
            + " | caller interrupt flag after: "
            + Thread.interrupted());
    assertFalse(
        "invokeAll ended (" + describe(thrown) + ") while task 0 was still running",
        task0RunningAtReturn);
  }

  /** Test 2: same as test 1, but task 0 fails once released: its exception must not be lost. */
  public void testInterruptDuringCallerTaskLosesTaskException() throws Exception {
    Scenario s = new Scenario(true, true);
    TaskExecutor te = new TaskExecutor(s.executor);
    Throwable thrown = null;
    AtomicBoolean done = new AtomicBoolean();
    Thread releaser = releaseWhenParked(Thread.currentThread(), s.release, done);
    try {
      te.invokeAll(s.callables);
    } catch (Throwable t) {
      thrown = t;
    } finally {
      done.set(true);
      s.release.countDown();
      releaser.join();
      s.executor.joinAll();
    }
    Thread.interrupted();
    System.out.println(
        "[test2] invokeAll outcome: "
            + describe(thrown)
            + " | task 0 exception reported: "
            + contains(thrown, s.late));
    assertTrue(
        "task 0's exception was never reported; invokeAll ended with " + describe(thrown),
        contains(thrown, s.late));
  }

  /**
   * Test 3: asynchronous interrupt while the caller is parked in Future#get inside collectResults.
   * The worker task is released only after the caller has finished (or after 5 s).
   */
  public void testAsyncInterruptWhileWaitingLeavesTaskRunning() throws Exception {
    Scenario s = new Scenario(false, false);
    TaskExecutor te = new TaskExecutor(s.executor);
    AtomicReference<Throwable> outcome = new AtomicReference<>();
    AtomicBoolean task0RunningAtReturn = new AtomicBoolean();
    Thread caller =
        new Thread(
            () -> {
              Throwable thrown = null;
              try {
                te.invokeAll(s.callables);
              } catch (Throwable t) {
                thrown = t;
              }
              task0RunningAtReturn.set(s.task0Finished.get() == false);
              outcome.set(thrown);
            },
            "verify-caller");
    caller.start();
    // wait until the caller is parked in FutureTask.awaitDone (called from collectResults)
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (true) {
      if (parkedInCollectResults(caller)) {
        break;
      }
      assertTrue("caller never parked in collectResults", System.nanoTime() < deadline);
      Thread.sleep(5);
    }
    caller.interrupt();
    caller.join(TimeUnit.SECONDS.toMillis(5)); // a correct invokeAll is still waiting here
    boolean callerDoneBeforeRelease = caller.isAlive() == false;
    s.release.countDown();
    caller.join(TimeUnit.SECONDS.toMillis(10));
    assertFalse(caller.isAlive());
    s.executor.joinAll();
    System.out.println(
        "[test3] invokeAll outcome: "
            + describe(outcome.get())
            + " | invokeAll ended before task 0 was released: "
            + callerDoneBeforeRelease
            + " | task 0 still running when invokeAll ended: "
            + task0RunningAtReturn.get());
    assertFalse(
        "invokeAll ended (" + describe(outcome.get()) + ") while task 0 was still running",
        task0RunningAtReturn.get());
  }

  /**
   * Test 4: end-to-end through IndexSearcher#search(Query, CollectorManager). Two segments, one
   * slice each. The slice on the executor keeps collecting after search() has thrown; the caller,
   * as usual, closes its reader when search() throws; the leftover slice then reads a closed
   * reader and its failure is never reported to anyone.
   */
  public void testIndexSearcherSliceOutlivesSearch() throws Exception {
    ByteBuffersDirectory dir = new ByteBuffersDirectory();
    try (IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      for (int seg = 0; seg < 2; seg++) {
        for (int i = 0; i < 10; i++) {
          Document d = new Document();
          d.add(new NumericDocValuesField("f", i));
          w.addDocument(d);
        }
        w.commit();
      }
    }
    HandOffExecutor executor = new HandOffExecutor();
    CountDownLatch release = new CountDownLatch(1);
    AtomicBoolean workerSliceFinished = new AtomicBoolean();
    AtomicReference<Throwable> workerSliceFailure = new AtomicReference<>();
    DirectoryReader reader = DirectoryReader.open(dir);
    assertEquals(2, reader.leaves().size());
    IndexSearcher searcher =
        new IndexSearcher(reader, executor) {
          @Override
          protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
            return slices(leaves, 1, 1, false);
          }
        };
    searcher.setQueryCache(null);
    CollectorManager<Collector, Integer> manager =
        new CollectorManager<>() {
          @Override
          public Collector newCollector() {
            return new Collector() {
              @Override
              public LeafCollector getLeafCollector(LeafReaderContext context) throws IOException {
                if (onWorker()) {
                  executor.started.countDown();
                  awaitUninterruptibly(release);
                  try {
                    // a normal slice reads per-segment data while collecting
                    context.reader().getNumericDocValues("f");
                  } catch (Throwable t) {
                    workerSliceFailure.set(t);
                    throw t;
                  } finally {
                    workerSliceFinished.set(true);
                  }
                } else {
                  Thread.currentThread().interrupt(); // caller gets interrupted mid-search
                }
                return new LeafCollector() {
                  @Override
                  public void setScorer(Scorable scorer) {}

                  @Override
                  public void collect(int doc) {}
                };
              }

              @Override
              public ScoreMode scoreMode() {
                return ScoreMode.COMPLETE_NO_SCORES;
              }
            };
          }

          @Override
          public Integer reduce(Collection<Collector> collectors) {
            return collectors.size();
          }
        };
    Throwable thrown = null;
    boolean sliceRunningAtReturn;
    AtomicBoolean done = new AtomicBoolean();
    Thread releaser = releaseWhenParked(Thread.currentThread(), release, done);
    try {
      searcher.search(new MatchAllDocsQuery(), manager); // ctor (not INSTANCE) so the test also compiles against 10.1.0
    } catch (Throwable t) {
      thrown = t;
    } finally {
      sliceRunningAtReturn = workerSliceFinished.get() == false;
      done.set(true);
      Thread.interrupted();
      reader.close(); // what try-with-resources / SearcherManager.release would do
      release.countDown();
      releaser.join();
      executor.joinAll();
      dir.close();
    }
    System.out.println(
        "[test4] search outcome: "
            + describe(thrown)
            + " | slice still running when search ended: "
            + sliceRunningAtReturn
            + " | leftover slice failure (unreported): "
            + workerSliceFailure.get());
    assertFalse(
        "IndexSearcher#search ended (" + describe(thrown) + ") while a slice was still running",
        sliceRunningAtReturn);
  }

  /** Control: without an interrupt, invokeAll waits for task 0 and reports its exception. */
  public void testControlNoInterrupt() throws Exception {
    Scenario s = new Scenario(true, false);
    TaskExecutor te = new TaskExecutor(s.executor);
    Thread releaser =
        new Thread(
            () -> {
              try {
                Thread.sleep(200);
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
              s.release.countDown();
            });
    releaser.start();
    Throwable thrown = null;
    try {
      te.invokeAll(s.callables);
    } catch (Throwable t) {
      thrown = t;
    }
    releaser.join();
    s.executor.joinAll();
    System.out.println("[control] invokeAll outcome: " + describe(thrown));
    assertTrue(s.task0Finished.get());
    assertSame(s.late, thrown);
  }
}
