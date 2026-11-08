package org.apache.lucene.index;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ThreadInterruptedException;

public class TestRollbackWithThrowingMergeFinished extends LuceneTestCase {

  // Expected: rollback() returns; actual: it waits forever for the aborted merge to unregister.
  public void testRollbackReturns() throws Exception {
    CountDownLatch taken = new CountDownLatch(1);
    CountDownLatch aborted = new CountDownLatch(1);
    MergePolicy mergePolicy =
        new FilterMergePolicy(NoMergePolicy.INSTANCE) {
          @Override
          public MergeSpecification findMerges(
              MergeTrigger trigger, SegmentInfos infos, MergeContext context) {
            if (trigger != MergeTrigger.EXPLICIT) {
              return null;
            }
            MergeSpecification spec = new MergeSpecification();
            spec.add(
                new OneMerge(infos.asList()) {
                  @Override
                  public void setAborted() {
                    super.setAborted();
                    aborted.countDown();
                  }

                  @Override
                  public void mergeFinished(boolean success, boolean segmentDropped)
                      throws IOException {
                    throw new IOException("mergeFinished failed");
                  }
                });
            return spec;
          }
        };
    MergeScheduler mergeScheduler =
        new MergeScheduler() {
          @Override
          public void merge(MergeSource source, MergeTrigger trigger) throws IOException {
            MergePolicy.OneMerge merge = source.getNextMerge();
            if (merge != null) {
              taken.countDown();
              try {
                aborted.await();
              } catch (InterruptedException e) {
                throw new ThreadInterruptedException(e);
              }
              source.merge(merge);
            }
          }

          @Override
          public void close() {}
        };
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w =
          new IndexWriter(
              dir,
              new IndexWriterConfig().setMergePolicy(mergePolicy).setMergeScheduler(mergeScheduler));
      w.addDocument(new Document());
      w.flush();
      w.addDocument(new Document());
      w.flush();
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread rollback =
          new Thread(
              () -> {
                try {
                  taken.await();
                  w.rollback();
                } catch (Throwable t) {
                  failure.set(t);
                }
              });
      rollback.start();
      expectThrows(IOException.class, w::maybeMerge);
      rollback.join(10_000);
      boolean hung = rollback.isAlive();
      rollback.interrupt();
      rollback.join();
      assertFalse("rollback() did not return", hung);
      assertNull(failure.get());
    }
  }
}
