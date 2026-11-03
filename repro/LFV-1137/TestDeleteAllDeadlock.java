package org.apache.lucene.index;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOSupplier;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class TestDeleteAllDeadlock extends LuceneTestCase {

  // Expected: deleteAll, a flushing addDocument and tryDeleteDocument all return; actual: they deadlock.
  public void testDeleteAllWithFlushAndTryDelete() throws Exception {
    Semaphore resume = new Semaphore(0);
    MergePolicy mp =
        new FilterMergePolicy(NoMergePolicy.INSTANCE) {
          @Override
          public boolean keepFullyDeletedSegment(IOSupplier<CodecReader> reader) {
            if (Thread.currentThread().getName().equals("tryDelete")) {
              resume.acquireUninterruptibly();
            }
            return false;
          }
        };
    IndexWriter w =
        new IndexWriter(
            new ByteBuffersDirectory(),
            new IndexWriterConfig().setMergePolicy(mp).setMaxBufferedDocs(2));
    w.addDocument(new Document());
    DirectoryReader r = DirectoryReader.open(w);

    Thread tryDelete = start("tryDelete", () -> w.tryDeleteDocument(r, 0));
    awaitState(tryDelete, Thread.State.WAITING);
    Thread indexer =
        start(
            "indexer",
            () -> {
              w.addDocument(new Document());
              return w.addDocument(new Document());
            });
    awaitState(indexer, Thread.State.BLOCKED);
    Thread deleteAll = start("deleteAll", w::deleteAll);
    awaitState(deleteAll, Thread.State.WAITING);
    resume.release();

    ThreadMXBean mx = ManagementFactory.getThreadMXBean();
    while (tryDelete.isAlive() && mx.findDeadlockedThreads() == null) {
      Thread.onSpinWait();
    }
    long[] deadlocked = mx.findDeadlockedThreads();
    if (deadlocked != null) {
      fail(Arrays.toString(mx.getThreadInfo(deadlocked, true, true)));
    }
    indexer.join();
    deleteAll.join();
    r.close();
    w.close();
  }

  private static Thread start(String name, Callable<?> body) {
    Thread t =
        new Thread(
            () -> {
              try {
                body.call();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            },
            name);
    t.start();
    return t;
  }

  private static void awaitState(Thread t, Thread.State state) {
    while (t.getState() != state) {
      assertTrue(t.getName() + " ended early", t.isAlive());
      Thread.onSpinWait();
    }
  }
}
