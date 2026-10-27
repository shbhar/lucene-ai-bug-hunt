package org.apache.lucene.facet;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.IndexSearcher.LeafSlice;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.NamedThreadFactory;

/**
 * Fact-check probe: two slices of very different size. Segment A has 10,000 docs labelled color/x,
 * segment B has 100 docs labelled color/y; sampleSize 1,000; one slice per segment. Prints the
 * amortized count of each label for one slice (no executor) and for two slices.
 */
public class TestFactCheckMixedSlices extends LuceneTestCase {
  private static final FacetsConfig CONFIG = new FacetsConfig();

  public void testMixedSliceSizes() throws Exception {
    ExecutorService executor =
        Executors.newFixedThreadPool(2, new NamedThreadFactory("TestFactCheckMixedSlices"));
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w =
        new IndexWriter(
            dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      for (int i = 0; i < 10_000; i++) {
        Document d = new Document();
        d.add(new SortedSetDocValuesFacetField("color", "x"));
        w.addDocument(CONFIG.build(d));
      }
      w.commit();
      for (int i = 0; i < 100; i++) {
        Document d = new Document();
        d.add(new SortedSetDocValuesFacetField("color", "y"));
        w.addDocument(CONFIG.build(d));
      }
      w.commit();
    }
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(2, reader.leaves().size());
      IndexSearcher one = new IndexSearcher(reader);
      IndexSearcher two =
          new IndexSearcher(reader, executor) {
            @Override
            protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
              return IndexSearcher.slices(leaves, 1, 1, false);
            }
          };
      String a = run(one);
      String b = run(two);
      System.out.println("FACTCHECK one slice: " + a);
      System.out.println("FACTCHECK two slices: " + b);
    } finally {
      dir.close();
      TestUtil.shutdownExecutorService(executor);
    }
  }

  private static String run(IndexSearcher s) throws Exception {
    RandomSamplingFacetsCollector fc =
        s.search(new MatchAllDocsQuery(), RandomSamplingFacetsCollector.createManager(1000, 42L));
    SortedSetDocValuesReaderState state =
        new DefaultSortedSetDocValuesReaderState(s.getIndexReader(), CONFIG);
    FacetResult counts = new SortedSetDocValuesFacetCounts(state, fc).getTopChildren(10, "color");
    FacetResult am = fc.amortizeFacetCounts(counts, CONFIG, s);
    StringBuilder sb = new StringBuilder();
    sb.append("slices=").append(s.getSlices().length).append(" rate=").append(fc.getSamplingRate());
    for (int i = 0; i < counts.labelValues.length; i++) {
      sb.append(" | ")
          .append(counts.labelValues[i].label)
          .append(": sampled ")
          .append(counts.labelValues[i].value)
          .append(", amortized ")
          .append(am.labelValues[i].value);
    }
    return sb.toString();
  }
}
