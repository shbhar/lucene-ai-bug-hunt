package org.apache.lucene.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ThreadInterruptedException;

public class TestAddIndexesMergeAfterRollback extends LuceneTestCase {

  public void testNoMergeThreadStartsAfterRollback() throws Exception {
    Directory srcDir = new ByteBuffersDirectory();
    try (IndexWriter src = new IndexWriter(srcDir, new IndexWriterConfig())) {
      for (int i = 0; i < 10; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(i), Field.Store.YES));
        src.addDocument(doc);
      }
    }
    DirectoryReader srcReader = DirectoryReader.open(srcDir);
    List<CodecReader> leaves = new ArrayList<>();
    for (LeafReaderContext ctx : srcReader.leaves()) {
      leaves.add(SlowCodecReaderWrapper.wrap(ctx.reader()));
    }
    CodecReader[] codecReaders = leaves.toArray(new CodecReader[0]);

    CountDownLatch inFindMerges = new CountDownLatch(1);
    CountDownLatch rollbackReturned = new CountDownLatch(1);
    AtomicBoolean writerRolledBack = new AtomicBoolean();
    List<String> mergeThreadsCreatedAfterRollback = Collections.synchronizedList(new ArrayList<>());
    List<Throwable> mergeThreadExceptions = Collections.synchronizedList(new ArrayList<>());

    ConcurrentMergeScheduler cms =
        new ConcurrentMergeScheduler() {
          @Override
          protected synchronized MergeThread getMergeThread(
              MergeSource mergeSource, MergePolicy.OneMerge merge) throws IOException {
            MergeThread thread = super.getMergeThread(mergeSource, merge);
            if (writerRolledBack.get()) {
              mergeThreadsCreatedAfterRollback.add(thread.getName());
            }
            return thread;
          }

          @Override
          protected void handleMergeException(Throwable exc) {
            mergeThreadExceptions.add(exc);
          }
        };
    MergePolicy mp =
        new FilterMergePolicy(new TieredMergePolicy()) {
          @Override
          public MergeSpecification findMerges(CodecReader... readers) throws IOException {
            // Deterministic interleaving: a concurrent rollback() runs to completion while
            // addIndexes(CodecReader...) is between its "is the writer open" check and the
            // registration of its merges.
            inFindMerges.countDown();
            try {
              assertTrue(rollbackReturned.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
              throw new ThreadInterruptedException(e);
            }
            return super.findMerges(readers);
          }
        };
    Directory dir = new ByteBuffersDirectory();
    IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergeScheduler(cms).setMergePolicy(mp));
    AtomicReference<Throwable> addIndexesFailure = new AtomicReference<>();
    Thread adder =
        new Thread(
            () -> {
              try {
                w.addIndexes(codecReaders);
              } catch (Throwable t) {
                addIndexesFailure.set(t);
              }
            });
    adder.start();
    assertTrue(inFindMerges.await(30, TimeUnit.SECONDS));

    w.rollback(); // closes the writer: aborts and waits for merges, closes the merge scheduler
    writerRolledBack.set(true);
    rollbackReturned.countDown();

    adder.join();
    cms.sync();
    srcReader.close();
    srcDir.close();
    dir.close();

    String details =
        "addIndexes threw: "
            + addIndexesFailure.get()
            + "; merge thread exceptions: "
            + mergeThreadExceptions;
    assertTrue("addIndexes on a rolled-back writer must fail; " + details, addIndexesFailure.get() != null);
    assertEquals(
        "merge threads were started after rollback() returned; " + details,
        List.of(),
        mergeThreadsCreatedAfterRollback);
  }
}
