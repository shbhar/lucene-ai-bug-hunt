package org.example.cms;

import static org.junit.Assert.assertEquals;

import org.apache.lucene.index.ConcurrentMergeScheduler;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.Test;

public class TestCmsAutoDetectJavadoc {

  // Javadoc of AUTO_DETECT_MERGES_AND_THREADS: maxThreadCount = max(1, min(4, cpuCoreCount/2)), 4 at 10 cores; actual: 5.
  @Test
  public void testDefaultMaxThreadCountAtTenCores() throws Exception {
    System.setProperty(ConcurrentMergeScheduler.DEFAULT_CPU_CORE_COUNT_PROPERTY, "10");
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
      ConcurrentMergeScheduler cms =
          (ConcurrentMergeScheduler) writer.getConfig().getMergeScheduler();
      assertEquals(4, cms.getMaxThreadCount());
    } finally {
      System.clearProperty(ConcurrentMergeScheduler.DEFAULT_CPU_CORE_COUNT_PROPERTY);
    }
  }
}
