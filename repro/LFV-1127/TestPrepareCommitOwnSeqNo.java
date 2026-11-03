package org.apache.lucene.index;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ThreadInterruptedException;

public class TestPrepareCommitOwnSeqNo extends LuceneTestCase {

  // Expected: prepareCommit() returns the seqNo that commit() returned for the same commit (3);
  // actual: 6, the seqNo of the next commit.
  public void testPrepareCommitReturnsItsOwnSeqNo() throws Exception {
    CountDownLatch inMerge = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setMergePolicy(
                new FilterMergePolicy(NoMergePolicy.INSTANCE) {
                  @Override
                  public MergeSpecification findMerges(
                      MergeTrigger trigger, SegmentInfos infos, MergeContext context) {
                    return trigger == MergeTrigger.FULL_FLUSH ? new MergeSpecification() : null;
                  }
                })
            .setMergeScheduler(
                new MergeScheduler() {
                  @Override
                  public void merge(MergeSource source, MergeTrigger trigger) {
                    if (inMerge.getCount() > 0) {
                      inMerge.countDown();
                      try {
                        release.await();
                      } catch (InterruptedException e) {
                        throw new ThreadInterruptedException(e);
                      }
                    }
                  }
                });
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(new Document());
      FutureTask<Long> prepare = new FutureTask<>(w::prepareCommit);
      new Thread(prepare).start();
      inMerge.await();
      long committed = w.commit();
      w.addDocument(new Document());
      w.prepareCommit();
      release.countDown();
      long prepared = prepare.get();
      w.commit();
      assertEquals(committed, prepared);
    }
  }
}
