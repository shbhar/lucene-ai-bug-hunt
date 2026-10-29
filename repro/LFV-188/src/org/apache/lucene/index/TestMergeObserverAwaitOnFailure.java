package org.apache.lucene.index;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMergeObserverAwaitOnFailure extends LuceneTestCase {

  public void testAwaitAfterFailedMerge() throws Exception {
    Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexOutput createOutput(String name, IOContext context) throws IOException {
            if (context.context() == IOContext.Context.MERGE) {
              throw new IOException("simulated disk full");
            }
            return super.createOutput(name, context);
          }
        };
    ConcurrentMergeScheduler cms =
        new ConcurrentMergeScheduler() {
          @Override
          protected void handleMergeException(Throwable exc) {}
        };
    TieredMergePolicy mp = new TieredMergePolicy();
    mp.setForceMergeDeletesPctAllowed(0);
    IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergeScheduler(cms).setMergePolicy(mp));
    for (int i = 0; i < 10; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
      w.addDocument(doc);
    }
    w.deleteDocuments(new Term("id", "0"));
    MergePolicy.MergeObserver observer = w.forceMergeDeletes(false);
    assertEquals("forced-deletes merges scheduled", 1, observer.numMerges());
    boolean success = observer.await();
    cms.sync();
    assertNotNull("the forced-deletes merge must have failed", w.getTragicException());
    w.close();
    dir.close();
    // MergeObserver#await() javadoc: "true if all merges completed successfully ..., false on error"
    assertFalse("expected await() == false after the failed merge, got true", success);
  }
}
