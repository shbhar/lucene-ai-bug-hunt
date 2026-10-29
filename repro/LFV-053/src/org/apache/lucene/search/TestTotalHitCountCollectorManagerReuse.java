package org.apache.lucene.search;

import java.util.Collection;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTotalHitCountCollectorManagerReuse extends LuceneTestCase {

  public void testReuseAfterFailedSearch() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(new Document());
        w.addDocument(new Document());
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher =
            new IndexSearcher(reader, Runnable::run) {
              @Override
              protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
                return slices(leaves, 1, 1, true); // one partition per document
              }
            };
        TotalHitCountCollectorManager countManager =
            new TotalHitCountCollectorManager(searcher.getSlices());
        CollectorManager<Collector, Void> failing =
            new CollectorManager<>() {
              @Override
              public Collector newCollector() {
                return new SimpleCollector() {
                  @Override
                  public void collect(int doc) {
                    throw new IllegalStateException("application collector failure");
                  }

                  @Override
                  public ScoreMode scoreMode() {
                    return ScoreMode.COMPLETE_NO_SCORES;
                  }
                };
              }

              @Override
              public Void reduce(Collection<Collector> collectors) {
                return null;
              }
            };
        expectThrows(
            IllegalStateException.class,
            () ->
                searcher.search(
                    new MatchAllDocsQuery(), new MultiCollectorManager(countManager, failing)));
        assertEquals(
            "count of the next search with the same manager",
            2,
            (int) searcher.search(new MatchAllDocsQuery(), countManager));
      }
    }
  }
}
