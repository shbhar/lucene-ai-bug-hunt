package org.apache.lucene.index;

import com.carrotsearch.randomizedtesting.annotations.Timeout;
import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestAddIndexesMergeFinishedThrows extends LuceneTestCase {

  // Expected: rollback() closes the writer after addIndexes reported the mergeFinished failure; actual: it never returns.
  @Timeout(millis = 10_000)
  public void testRollbackAfterMergeFinishedThrows() throws Exception {
    MergePolicy mp =
        new FilterMergePolicy(NoMergePolicy.INSTANCE) {
          @Override
          public MergeSpecification findMerges(CodecReader... readers) {
            MergeSpecification spec = new MergeSpecification();
            spec.add(
                new OneMerge(readers) {
                  @Override
                  public void mergeFinished(boolean success, boolean segmentDropped)
                      throws IOException {
                    throw new IOException("mergeFinished failed");
                  }
                });
            return spec;
          }
        };
    try (Directory src = newDirectory();
        Directory dest = newDirectory()) {
      try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig())) {
        w.addDocument(new Document());
      }
      try (DirectoryReader r = DirectoryReader.open(src)) {
        IndexWriter w =
            new IndexWriter(
                dest,
                new IndexWriterConfig()
                    .setMergePolicy(mp)
                    .setMergeScheduler(new SerialMergeScheduler()));
        expectThrows(IOException.class, () -> w.addIndexes((CodecReader) getOnlyLeafReader(r)));
        w.rollback();
        assertFalse(w.isOpen());
      }
    }
  }
}
