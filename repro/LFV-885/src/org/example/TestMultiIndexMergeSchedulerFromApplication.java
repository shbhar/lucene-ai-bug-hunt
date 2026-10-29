package org.example;

import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MergeScheduler;
import org.apache.lucene.index.MultiIndexMergeScheduler;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMultiIndexMergeSchedulerFromApplication extends LuceneTestCase {

  public void testCreateOnePerIndex() throws Exception {
    // Expected: compiles, as with ConcurrentMergeScheduler; actual: javac "MultiIndexMergeScheduler is not public in org.apache.lucene.index".
    try (Directory dir = newDirectory();
        MergeScheduler scheduler = new MultiIndexMergeScheduler(dir)) {
      new IndexWriterConfig().setMergeScheduler(scheduler);
    }
  }
}
