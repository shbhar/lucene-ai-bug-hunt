/*
 * Independent verification of candidate:
 *   "Reusing TotalHitCountCollectorManager after a failed search silently returns a wrong count
 *    (or hangs): stale earlyTerminatedMap"
 *
 * Written from scratch against Lucene main 7cecf79523. Everything runs on the calling thread
 * (executor = Runnable::run), so the task order is deterministic: slice 0 = segment A docs [0,5),
 * slice 1 = segment A docs [5,10), slice 2 = segment B (3 docs).
 */
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.ExitableDirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.QueryTimeout;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MultiCollectorManager;
import org.apache.lucene.search.Scorable;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopFieldCollectorManager;
import org.apache.lucene.search.TotalHitCountCollectorManager;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestReusedCountManagerAfterFailure extends LuceneTestCase {

  private static final Executor CALLER_RUNS = Runnable::run;

  /** Segment A: 10 docs, segment B: 3 docs. All docs have f=x and a numeric DV "price". */
  private static Directory buildIndex() throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 13; i++) {
        Document d = new Document();
        d.add(new StringField("f", "x", Field.Store.NO));
        d.add(new NumericDocValuesField("price", i));
        w.addDocument(d);
        if (i == 9) {
          w.commit(); // segment A = first 10 docs
        }
      }
      w.commit(); // segment B = last 3 docs
    }
    return dir;
  }

  /**
   * Searcher that opts into intra-segment concurrency the documented way (override slices(), call
   * the static slices(..., allowSegmentPartitions=true)). maxDocsPerSlice=5 splits segment A into
   * [0,5) and [5,10). An optional hook runs at the start of every searchLeaf call.
   */
  private static final class PartitionedSearcher extends IndexSearcher {
    final AtomicReference<java.util.function.BiConsumer<LeafReaderContext, Integer>> hook =
        new AtomicReference<>((c, m) -> {});

    PartitionedSearcher(DirectoryReader r) {
      super(r, CALLER_RUNS);
      setQueryCache(null); // remove LRUQueryCache from the picture (it does not matter here)
    }

    @Override
    protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
      return slices(leaves, 5, 1, true);
    }

    @Override
    protected void searchLeaf(
        LeafReaderContext ctx, int minDocId, int maxDocId, Weight weight, Collector collector)
        throws IOException {
      hook.get().accept(ctx, minDocId);
      super.searchLeaf(ctx, minDocId, maxDocId, weight, collector);
    }
  }

  private static void assertLayout(IndexSearcher s) {
    IndexSearcher.LeafSlice[] slices = s.getSlices();
    assertEquals(3, slices.length);
    assertEquals(2, s.getIndexReader().leaves().size());
    assertEquals(10, s.getIndexReader().leaves().get(0).reader().maxDoc());
    assertEquals(3, s.getIndexReader().leaves().get(1).reader().maxDoc());
    assertEquals(0, slices[0].partitions[0].minDocId);
    assertEquals(5, slices[0].partitions[0].maxDocId);
    assertEquals(5, slices[1].partitions[0].minDocId);
    assertEquals(10, slices[1].partitions[0].maxDocId);
  }

  /** A user collector that fails (application error) when it collects a doc of segment ord 1. */
  private static CollectorManager<Collector, Void> failOnSecondSegment() {
    return new CollectorManager<>() {
      @Override
      public Collector newCollector() {
        return new Collector() {
          @Override
          public LeafCollector getLeafCollector(LeafReaderContext context) {
            final boolean fail = context.ord == 1;
            return new LeafCollector() {
              @Override
              public void setScorer(Scorable scorer) {}

              @Override
              public void collect(int doc) {
                if (fail) {
                  throw new IllegalStateException("simulated application failure on segment B");
                }
              }
            };
          }

          @Override
          public ScoreMode scoreMode() {
            return ScoreMode.COMPLETE_NO_SCORES;
          }
        };
      }

      @Override
      public Void reduce(java.util.Collection<Collector> collectors) {
        return null;
      }
    };
  }

  /** Control: reuse after successful searches is correct (the code says reuse is supported). */
  public void testControlReuseAfterSuccess() throws Exception {
    try (Directory dir = buildIndex();
        DirectoryReader r = DirectoryReader.open(dir)) {
      PartitionedSearcher s = new PartitionedSearcher(r);
      assertLayout(s);
      TotalHitCountCollectorManager m = new TotalHitCountCollectorManager(s.getSlices());
      assertEquals(13, (int) s.search(new MatchAllDocsQuery(), m));
      assertEquals(13, (int) s.search(new TermQuery(new Term("f", "x")), m));
      assertEquals(13, (int) s.search(new MatchAllDocsQuery(), m));
      // and IndexSearcher#count (fresh manager each time) agrees
      assertEquals(13, s.count(new MatchAllDocsQuery()));
    }
  }

  /**
   * Minimal: count + an application collector via MultiCollectorManager (the usage the
   * TotalHitCountCollectorManager javadoc recommends). The application collector throws on segment
   * B. A later count-only search with the same TotalHitCountCollectorManager is silently wrong.
   */
  public void testReuseAfterCollectorFailureUndercounts() throws Exception {
    try (Directory dir = buildIndex();
        DirectoryReader r = DirectoryReader.open(dir)) {
      PartitionedSearcher s = new PartitionedSearcher(r);
      assertLayout(s);
      TotalHitCountCollectorManager m = new TotalHitCountCollectorManager(s.getSlices());

      IllegalStateException e =
          expectThrows(
              IllegalStateException.class,
              () -> s.search(new MatchAllDocsQuery(), new MultiCollectorManager(m, failOnSecondSegment())));
      assertTrue(e.getMessage(), e.getMessage().contains("simulated application failure"));

      // a different query on the next request: the stale decision is query-independent
      int second = s.search(new TermQuery(new Term("f", "x")), m);
      int third = s.search(new TermQuery(new Term("f", "x")), m);
      int fresh = s.search(new TermQuery(new Term("f", "x")), new TotalHitCountCollectorManager(s.getSlices()));
      System.out.println(
          "[collector-failure] reused manager: second search=" + second + ", third search=" + third
              + "; fresh manager=" + fresh + "; IndexSearcher#count=" + s.count(new TermQuery(new Term("f", "x"))));
      assertEquals("reused manager after failed search", 13, second);
    }
  }

  /**
   * Realistic trigger: ExitableDirectoryReader (documented timeout mechanism, throws
   * ExitingReaderException) + count + top-5 sorted by a numeric doc-values field. The deadline
   * passes while segment B is being searched. The application resets its timeout and runs the next
   * request with the same TotalHitCountCollectorManager.
   */
  public void testReuseAfterExitableReaderTimeoutUndercounts() throws Exception {
    AtomicBoolean expired = new AtomicBoolean(false);
    QueryTimeout timeout = expired::get;
    try (Directory dir = buildIndex();
        DirectoryReader r = new ExitableDirectoryReader(DirectoryReader.open(dir), timeout)) {
      PartitionedSearcher s = new PartitionedSearcher(r);
      assertLayout(s);
      TotalHitCountCollectorManager m = new TotalHitCountCollectorManager(s.getSlices());
      Sort byPrice = new Sort(new SortField("price", SortField.Type.LONG));

      // the deadline passes when the searcher reaches segment B (ord 1)
      s.hook.set((ctx, minDoc) -> {
        if (ctx.ord == 1) expired.set(true);
      });
      ExitableDirectoryReader.ExitingReaderException e =
          expectThrows(
              ExitableDirectoryReader.ExitingReaderException.class,
              () ->
                  s.search(
                      new MatchAllDocsQuery(),
                      new MultiCollectorManager(
                          m, new TopFieldCollectorManager(byPrice, 5, null, Integer.MAX_VALUE))));
      System.out.println("[timeout] first search failed as expected: " + e);

      // next request: fresh deadline, no hook, same count manager
      s.hook.set((c, md) -> {});
      expired.set(false);
      Object[] res =
          s.search(
              new MatchAllDocsQuery(),
              new MultiCollectorManager(
                  m, new TopFieldCollectorManager(byPrice, 5, null, Integer.MAX_VALUE)));
      int count = (Integer) res[0];
      org.apache.lucene.search.TopFieldDocs top = (org.apache.lucene.search.TopFieldDocs) res[1];
      System.out.println(
          "[timeout] second search: count="
              + count
              + " topDocs.totalHits="
              + top.totalHits
              + " firstDoc="
              + top.scoreDocs[0].doc);
      int third = s.search(new MatchAllDocsQuery(), m);
      System.out.println("[timeout] third search with reused manager (map cleared by 2nd reduce): " + third);
      assertEquals("reused manager after timed-out search", 13, count);
    }
  }

  /**
   * Hang variant: the first search fails inside Weight#count on the first partition of segment A
   * (ExitableDirectoryReader timeout during TermWeight#count -> Terms#iterator). The future put in
   * earlyTerminatedMap is never completed and never removed. The next search with the same manager
   * blocks forever in CompletableFuture#get, even though everything runs on one thread.
   */
  public void testReuseAfterFailureInCountHangs() throws Exception {
    AtomicBoolean expired = new AtomicBoolean(false);
    QueryTimeout timeout = expired::get;
    try (Directory dir = buildIndex();
        DirectoryReader r = new ExitableDirectoryReader(DirectoryReader.open(dir), timeout)) {
      PartitionedSearcher s = new PartitionedSearcher(r);
      assertLayout(s);
      TotalHitCountCollectorManager m = new TotalHitCountCollectorManager(s.getSlices());

      // weight (and its TermStates) is built before the deadline; it passes at the first leaf
      s.hook.set((ctx, minDoc) -> expired.set(true));
      expectThrows(
          ExitableDirectoryReader.ExitingReaderException.class,
          () -> s.search(new TermQuery(new Term("f", "x")), m));

      s.hook.set((c, md) -> {});
      expired.set(false);
      AtomicReference<Object> outcome = new AtomicReference<>();
      Thread t =
          new Thread(
              () -> {
                try {
                  outcome.set(s.search(new MatchAllDocsQuery(), m));
                } catch (Throwable th) {
                  outcome.set(th);
                }
              },
              "second-search");
      t.start();
      t.join(5000);
      boolean stuck = t.isAlive();
      List<String> frames = new ArrayList<>();
      for (StackTraceElement el : t.getStackTrace()) frames.add(el.toString());
      System.out.println("[hang] alive after 5s=" + stuck + " state=" + t.getState());
      for (int i = 0; i < Math.min(frames.size(), 12); i++) System.out.println("    at " + frames.get(i));
      if (stuck) {
        t.interrupt(); // CompletableFuture#get is interruptible; clean up the thread
        t.join(5000);
        System.out.println("[hang] after interrupt: " + outcome.get());
        // the pending future is still in the map: the manager is permanently poisoned
        AtomicReference<Object> outcome2 = new AtomicReference<>();
        Thread t2 =
            new Thread(
                () -> {
                  try {
                    outcome2.set(s.search(new MatchAllDocsQuery(), m));
                  } catch (Throwable th) {
                    outcome2.set(th);
                  }
                },
                "third-search");
        t2.start();
        t2.join(2000);
        System.out.println("[hang] third search alive after 2s=" + t2.isAlive() + " state=" + t2.getState());
        if (t2.isAlive()) {
          t2.interrupt();
          t2.join(5000);
        }
        // a fresh manager on the same searcher is fine
        System.out.println("[hang] fresh manager: " + s.search(new MatchAllDocsQuery(), new TotalHitCountCollectorManager(s.getSlices())));
      }
      assertFalse("second search with reused manager is stuck in CompletableFuture.get", stuck);
      assertEquals(13, outcome.get());
    }
  }
}
