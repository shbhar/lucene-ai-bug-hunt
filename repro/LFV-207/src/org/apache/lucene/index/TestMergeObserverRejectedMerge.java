package org.apache.lucene.index;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ThreadInterruptedException;

/**
 * IndexWriter#forceMergeDeletes returns a MergeObserver over every merge the MergePolicy proposed,
 * including merges that IndexWriter refused to register because one of their segments is already
 * being merged. Those merges never run, so the observer never completes.
 */
public class TestMergeObserverRejectedMerge extends LuceneTestCase {

  /** Holds the first merge it is asked to run until released. */
  private static class HoldFirstMergeCMS extends ConcurrentMergeScheduler {
    final CountDownLatch firstMergeStarted = new CountDownLatch(1);
    final CountDownLatch releaseFirstMerge = new CountDownLatch(1);

    @Override
    protected void doMerge(MergeSource mergeSource, MergePolicy.OneMerge merge) throws IOException {
      firstMergeStarted.countDown();
      try {
        assertTrue(releaseFirstMerge.await(30, TimeUnit.SECONDS));
      } catch (InterruptedException e) {
        throw new ThreadInterruptedException(e);
      }
      super.doMerge(mergeSource, merge);
    }
  }

  /** Number of merges the merge policy proposed to the last forceMergeDeletes call. */
  private final AtomicInteger proposedForcedDeletesMerges = new AtomicInteger();

  /** Three committed segments, each with one deleted document; a LogDocMergePolicy. */
  private IndexWriter newWriter(Directory dir, ConcurrentMergeScheduler cms) throws IOException {
    LogDocMergePolicy logDoc = new LogDocMergePolicy();
    logDoc.setMergeFactor(10); // no natural merges for the 3 segments below
    MergePolicy mp =
        new FilterMergePolicy(logDoc) {
          @Override
          public MergeSpecification findForcedDeletesMerges(
              SegmentInfos segmentInfos, MergeContext mergeContext) throws IOException {
            MergeSpecification spec = super.findForcedDeletesMerges(segmentInfos, mergeContext);
            proposedForcedDeletesMerges.set(spec == null ? 0 : spec.merges.size());
            return spec;
          }
        };
    IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergeScheduler(cms).setMergePolicy(mp));
    for (int seg = 0; seg < 3; seg++) {
      for (int i = 0; i < 10; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", seg + "-" + i, Field.Store.NO));
        w.addDocument(doc);
      }
      w.commit();
    }
    for (int seg = 0; seg < 3; seg++) {
      w.deleteDocuments(new Term("id", seg + "-0"));
    }
    w.commit();
    return w;
  }

  /** forceMergeDeletes(false) while a background merge runs: the observer never completes. */
  public void testNonBlockingObserverCompletes() throws Exception {
    HoldFirstMergeCMS cms = new HoldFirstMergeCMS();
    Directory dir = new ByteBuffersDirectory();
    IndexWriter w = newWriter(dir, cms);

    w.forceMerge(1, false); // a background merge of all segments
    assertTrue(cms.firstMergeStarted.await(30, TimeUnit.SECONDS));

    MergePolicy.MergeObserver observer = w.forceMergeDeletes(false);
    int numMerges = observer.numMerges();
    cms.releaseFirstMerge.countDown();

    w.forceMerge(1); // waits for the running merge
    cms.sync();
    w.close(); // no merge can run any more
    dir.close();

    assertTrue("expected the merge policy to propose merges", proposedForcedDeletesMerges.get() > 0);
    boolean completed = observer.await(10, TimeUnit.SECONDS);
    assertTrue(
        "MergeObserver never completes: "
            + observer.numCompletedMerges()
            + " of "
            + numMerges
            + " merges completed after the writer was closed; await() would block forever",
        completed);
  }

  /**
   * forceMergeDeletes(true) javadoc: "Since this method blocks until completion, merges will
   * already be complete when it returns."
   */
  public void testBlockingObserverIsComplete() throws Exception {
    HoldFirstMergeCMS cms = new HoldFirstMergeCMS();
    Directory dir = new ByteBuffersDirectory();
    IndexWriter w = newWriter(dir, cms);

    w.forceMerge(1, false);
    assertTrue(cms.firstMergeStarted.await(30, TimeUnit.SECONDS));

    MergePolicy.MergeObserver observer = w.forceMergeDeletes(true);
    int numMerges = observer.numMerges();
    int completedOnReturn = observer.numCompletedMerges();

    cms.releaseFirstMerge.countDown();
    w.forceMerge(1);
    cms.sync();
    w.close();
    dir.close();

    assertTrue("expected the merge policy to propose merges", proposedForcedDeletesMerges.get() > 0);
    assertEquals(
        "forceMergeDeletes(true) returned an observer whose merges are not complete",
        numMerges,
        completedOnReturn);
  }
}
