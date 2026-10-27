package verify.sampling;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector.MatchingDocs;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.RandomSamplingFacetsCollector;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.NamedThreadFactory;

/**
 * Independent check: the CollectorManager returned by RandomSamplingFacetsCollector.createManager
 * should give about sampleSize sampled hits (ratio sampleSize / totalHits, per the constructor
 * javadoc) and amortized counts close to the true counts, whatever the number of slices.
 *
 * <p>Only public API is used. Power-of-two sizes keep 1 / samplingRate integral, so the bin-size
 * truncation of createSample plays no part.
 */
public class TestSamplingManagerMultiSlice extends LuceneTestCase {

  private static final int NUM_LABELS = 4;
  private static final long SEED = 42L;

  /** Builds an index with the given segment sizes (one flush per segment, no merges). */
  private static DirectoryReader buildIndex(Directory dir, FacetsConfig config, int... segSizes)
      throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    iwc.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    iwc.setRAMBufferSizeMB(512);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      int id = 0;
      for (int segSize : segSizes) {
        for (int i = 0; i < segSize; i++, id++) {
          Document doc = new Document();
          doc.add(new SortedSetDocValuesFacetField("color", "c" + (id % NUM_LABELS)));
          w.addDocument(config.build(doc));
        }
        w.commit();
      }
    }
    DirectoryReader r = DirectoryReader.open(dir);
    assertEquals("precondition: segment count", segSizes.length, r.leaves().size());
    return r;
  }

  private record Stats(
      int slices, int totalHits, int sampled, double rate, int[] amortized, int[] sampledCounts) {
    String describe() {
      return "slices="
          + slices
          + " totalHits="
          + totalHits
          + " sampled="
          + sampled
          + " getSamplingRate="
          + rate
          + " effectiveRate="
          + ((double) sampled / totalHits)
          + " sampledLabelCounts="
          + java.util.Arrays.toString(sampledCounts)
          + " amortizedLabelCounts="
          + java.util.Arrays.toString(amortized);
    }
  }

  private static Stats measure(
      IndexSearcher searcher, FacetsConfig config, SortedSetDocValuesReaderState state, int sampleSize)
      throws IOException {
    CollectorManager<RandomSamplingFacetsCollector, RandomSamplingFacetsCollector> m =
        RandomSamplingFacetsCollector.createManager(sampleSize, SEED);
    RandomSamplingFacetsCollector c = searcher.search(new MatchAllDocsQuery(), m);
    int totalHits = 0;
    int sampled = 0;
    List<MatchingDocs> mds = c.getMatchingDocs();
    for (MatchingDocs md : mds) {
      totalHits += md.totalHits();
      DocIdSetIterator it = md.bits().iterator();
      while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
        sampled++;
      }
    }
    SortedSetDocValuesFacetCounts counts = new SortedSetDocValuesFacetCounts(state, c);
    FacetResult res = counts.getTopChildren(NUM_LABELS, "color");
    FacetResult am = c.amortizeFacetCounts(res, config, searcher);
    int[] amortized = new int[NUM_LABELS];
    int[] sampledCounts = new int[NUM_LABELS];
    if (res != null) {
      for (LabelAndValue lv : res.labelValues) {
        sampledCounts[Integer.parseInt(lv.label.substring(1))] = lv.value.intValue();
      }
      for (LabelAndValue lv : am.labelValues) {
        amortized[Integer.parseInt(lv.label.substring(1))] = lv.value.intValue();
      }
    }
    return new Stats(
        searcher.getSlices().length, totalHits, sampled, c.getSamplingRate(), amortized, sampledCounts);
  }

  /** The documented outcome, with a generous factor-2 tolerance. */
  private static void assertSampleMatchesContract(
      String what, Stats s, int sampleSize, int numDocs) {
    String msg = what + ": sampleSize=" + sampleSize + " " + s.describe();
    System.out.println(msg);
    assertEquals(msg, numDocs, s.totalHits);
    assertTrue(
        "sample size should be about sampleSize (ratio sampleSize/totalHits): " + msg,
        s.sampled >= sampleSize / 2 && s.sampled <= 2 * sampleSize);
    int trueCount = numDocs / NUM_LABELS;
    for (int i = 0; i < NUM_LABELS; i++) {
      assertTrue(
          "amortized count of c" + i + " should be about the true count " + trueCount + ": " + msg,
          s.amortized[i] >= trueCount / 2 && s.amortized[i] <= 2 * trueCount);
    }
  }

  // 4 segments of 2^18 docs: each is above the default 250,000 docs per slice, so the default
  // IndexSearcher slicing gives one slice per segment.
  private static final int SEG = 1 << 18;
  private static final int SAMPLE_A = 1 << 12; // ratio 2^12 / 2^20 = 1/256

  private void runIndexA(boolean withExecutor) throws Exception {
    FacetsConfig config = new FacetsConfig();
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader r = buildIndex(dir, config, SEG, SEG, SEG, SEG)) {
      SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(r, config);
      if (withExecutor == false) {
        Stats s = measure(new IndexSearcher(r), config, state, SAMPLE_A);
        assertEquals("precondition: one slice without executor", 1, s.slices);
        assertSampleMatchesContract("no executor (control)", s, SAMPLE_A, 4 * SEG);
      } else {
        ExecutorService ex = Executors.newFixedThreadPool(4, new NamedThreadFactory("v1-sampling"));
        try {
          Stats s = measure(new IndexSearcher(r, ex), config, state, SAMPLE_A);
          assertEquals("precondition: default slicing gives 4 slices", 4, s.slices);
          assertSampleMatchesContract("executor, default slicing", s, SAMPLE_A, 4 * SEG);
        } finally {
          ex.shutdown();
          assertTrue(ex.awaitTermination(30, TimeUnit.SECONDS));
        }
      }
    }
  }

  public void testControlNoExecutorOneSlice() throws Exception {
    runIndexA(false);
  }

  public void testDefaultSlicingWithExecutor() throws Exception {
    runIndexA(true);
  }

  // One segment of 2^16 docs split into 4 intra-segment partitions of 2^14 docs.
  private static final int ONE_SEG = 1 << 16;
  private static final int SAMPLE_B = 1 << 10; // ratio 2^10 / 2^16 = 1/64

  public void testIntraSegmentPartitions() throws Exception {
    FacetsConfig config = new FacetsConfig();
    ExecutorService ex = Executors.newFixedThreadPool(4, new NamedThreadFactory("v1-sampling"));
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader r = buildIndex(dir, config, ONE_SEG)) {
      SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(r, config);
      // control: same executor, default slicing (one slice, the segment is small)
      Stats control = measure(new IndexSearcher(r, ex), config, state, SAMPLE_B);
      assertEquals("precondition: one slice by default", 1, control.slices);
      assertSampleMatchesContract("executor, one slice (control)", control, SAMPLE_B, ONE_SEG);

      IndexSearcher partitioned =
          new IndexSearcher(r, ex) {
            @Override
            protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
              return IndexSearcher.slices(leaves, ONE_SEG / 4, 5, true);
            }
          };
      Stats s = measure(partitioned, config, state, SAMPLE_B);
      assertEquals("precondition: 4 partitions", 4, s.slices);
      assertSampleMatchesContract("executor, 4 intra-segment partitions", s, SAMPLE_B, ONE_SEG);
    } finally {
      ex.shutdown();
      assertTrue(ex.awaitTermination(30, TimeUnit.SECONDS));
    }
  }
}
