package org.apache.lucene.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ThreadInterruptedException;

/**
 * ConcurrentMergeScheduler#merge calls mergeSource.onMergeFinished(merge) for any Throwable thrown
 * after the merge was checked out of the IndexWriter: (1) also when the merge thread was already
 * started (updateMergeThreads() throws), so a merge keeps running after IndexWriter unregistered it;
 * (2) when the thread could not be created, onMergeFinished only unregisters the merge and never
 * completes it, so its MergeObserver never completes.
 */
public class TestCmsMergeFailurePath9x extends LuceneTestCase {

  private static void addSegments(IndexWriter w, int numSegments) throws IOException {
    for (int seg = 0; seg < numSegments; seg++) {
      for (int i = 0; i < 10; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", seg + "-" + i, Field.Store.NO));
        w.addDocument(doc);
      }
      w.commit();
    }
  }

  /**
   * A negative forced-merge rate is accepted by setForceMergeMBPerSec. forceMerge then fails with
   * IllegalArgumentException from updateMergeThreads() after the merge thread was started, and the
   * catch block unregisters the running merge. A retry registers a second merge of the same
   * segments, and the writer is closed by a tragic exception.
   */
  public void testFailureAfterThreadStartUnregistersRunningMerge() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    List<Throwable> mergeThreadExceptions = Collections.synchronizedList(new ArrayList<>());
    ConcurrentMergeScheduler cms =
        new ConcurrentMergeScheduler() {
          @Override
          protected void doMerge(MergeSource mergeSource, MergePolicy.OneMerge merge)
              throws IOException {
            try {
              assertTrue(release.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
              throw new ThreadInterruptedException(e);
            }
            super.doMerge(mergeSource, merge);
          }

          @Override
          protected void handleMergeException(Throwable exc) {
            mergeThreadExceptions.add(exc);
          }
        };
    cms.setForceMergeMBPerSec(-1.0); // accepted without validation
    Directory dir = new ByteBuffersDirectory();
    IndexWriter w =
        new IndexWriter(
            dir,
            new IndexWriterConfig().setMergeScheduler(cms).setMergePolicy(new TieredMergePolicy()));
    addSegments(w, 2);

    List<Throwable> forceMergeFailures = new ArrayList<>();
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        w.forceMerge(1, false);
      } catch (IllegalArgumentException e) {
        forceMergeFailures.add(e);
      }
    }
    int liveMergeThreads = cms.mergeThreadCount();
    release.countDown();
    cms.sync();
    Throwable tragedy = w.getTragicException();
    w.rollback();
    dir.close();

    assertEquals(2, forceMergeFailures.size());
    assertEquals(
        "both forceMerge calls failed, yet merge threads for the same segments are running"
            + " (afterwards: tragic exception = "
            + tragedy
            + ", merge thread exceptions = "
            + mergeThreadExceptions
            + ")",
        0,
        liveMergeThreads);
    assertNull(
        "the writer was closed by a tragic exception; merge thread exceptions: "
            + mergeThreadExceptions,
        tragedy);
  }
}
