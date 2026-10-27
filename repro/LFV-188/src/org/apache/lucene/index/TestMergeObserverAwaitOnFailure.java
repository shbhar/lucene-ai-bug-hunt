package org.apache.lucene.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMergeObserverAwaitOnFailure extends LuceneTestCase {

  public void testAwaitReportsFailedMerge() throws Exception {
    AtomicBoolean failMerges = new AtomicBoolean();
    Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexOutput createOutput(String name, IOContext context) throws IOException {
            if (failMerges.get() && context.context() == IOContext.Context.MERGE) {
              throw new IOException("simulated disk error while writing merged segment");
            }
            return super.createOutput(name, context);
          }
        };
    List<Throwable> mergeThreadFailures = Collections.synchronizedList(new ArrayList<>());
    ConcurrentMergeScheduler cms =
        new ConcurrentMergeScheduler() {
          @Override
          protected void handleMergeException(Throwable exc) {
            mergeThreadFailures.add(exc);
          }
        };
    TieredMergePolicy mp = new TieredMergePolicy();
    mp.setForceMergeDeletesPctAllowed(0.0);
    IndexWriterConfig iwc =
        new IndexWriterConfig(new MockAnalyzer(random()))
            .setMergeScheduler(cms)
            .setMergePolicy(mp)
            .setUseCompoundFile(false);
    IndexWriter w = new IndexWriter(dir, iwc);
    for (int seg = 0; seg < 2; seg++) {
      for (int i = 0; i < 10; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", seg + "-" + i, Field.Store.NO));
        w.addDocument(doc);
      }
      w.commit();
    }
    w.deleteDocuments(new Term("id", "0-0"), new Term("id", "1-0"));
    w.commit();

    failMerges.set(true);
    MergePolicy.MergeObserver observer = w.forceMergeDeletes(false);
    assertTrue("expected forceMergeDeletes to schedule a merge", observer.numMerges() > 0);

    boolean awaitResult = observer.await(60, TimeUnit.SECONDS);
    boolean awaitResultNoTimeout = observer.await();
    assertEquals(observer.numMerges(), observer.numCompletedMerges());
    // the observer completes before the merge thread reports its exception; wait for the thread:
    cms.sync();
    assertFalse("the merge must have failed", mergeThreadFailures.isEmpty());
    assertNotNull("the writer must have recorded the merge failure", w.getTragicException());
    w.close();
    dir.close();
    // MergeObserver#await javadoc: "@return true if all merges completed successfully or no merges
    // were needed, false on error"
    assertFalse("await(timeout) returned true although the merge failed", awaitResult);
    assertFalse("await() returned true although the merge failed", awaitResultNoTimeout);
  }
}
