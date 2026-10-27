import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.QueryTimeout;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldCollectorManager;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent verifier test (verify, candidate 4). Deterministic: plain IndexSearcher (no
 * newSearcher randomization), ByteBuffersDirectory, NoMergePolicy with explicit flushes, and a
 * counting QueryTimeout that answers false for the first N shouldExit() calls and true afterwards.
 *
 * <p>TimeLimitingBulkScorer checks shouldExit() before each window; the first window is
 * [0,INTERVAL=100). So "allow 1 check" means exactly docs 0..99 of the first segment get collected.
 */
public class TestTimedOutTotalHitsV3 extends LuceneTestCase {

  static final int NUM_DOCS_PER_SEG = 500;

  /** Answers false for the first {@code allowed} calls, true afterwards (sticky). */
  static final class CountingTimeout implements QueryTimeout {
    final int allowed;
    final AtomicInteger calls = new AtomicInteger();

    CountingTimeout(int allowed) {
      this.allowed = allowed;
    }

    @Override
    public boolean shouldExit() {
      return calls.getAndIncrement() >= allowed;
    }
  }

  private Directory buildIndex(int numSegments) throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      int id = 0;
      for (int s = 0; s < numSegments; s++) {
        for (int i = 0; i < NUM_DOCS_PER_SEG; i++) {
          Document d = new Document();
          d.add(new StringField("f", "x", Field.Store.NO));
          d.add(new StringField("g", "y", Field.Store.NO));
          d.add(new NumericDocValuesField("n", id++));
          w.addDocument(d);
        }
        w.flush();
      }
      w.commit();
    }
    return dir;
  }

  private static Query termQuery() {
    return new TermQuery(new Term("f", "x"));
  }

  /** A conjunction: no Weight#count shortcut, scored through a BulkScorer. */
  private static Query conjunction() {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("f", "x")), BooleanClause.Occur.MUST)
        .add(new TermQuery(new Term("g", "y")), BooleanClause.Occur.MUST)
        .build();
  }

  private static void report(String label, TopDocs td, IndexSearcher s, int trueCount) {
    System.out.println(
        String.format(
            "%-58s totalHits=%-10s relation=%-24s timedOut()=%-5s trueMatches=%d",
            label, td.totalHits, td.totalHits.relation(), s.timedOut(), trueCount));
  }

  /** The TotalHits contract: EQUAL_TO means value is the exact count. */
  private static boolean contractHolds(TopDocs td, int trueCount) {
    TotalHits th = td.totalHits;
    if (th.relation() == TotalHits.Relation.EQUAL_TO) {
      return th.value() == trueCount;
    }
    return th.value() <= trueCount;
  }

  public void testControlNoTimeout() throws Exception {
    try (Directory dir = buildIndex(1);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      s.setQueryCache(null);
      TopDocs td = s.search(termQuery(), new TopScoreDocCollectorManager(10, Integer.MAX_VALUE));
      report("control: no timeout, exact-count manager", td, s, NUM_DOCS_PER_SEG);
      assertEquals(new TotalHits(NUM_DOCS_PER_SEG, TotalHits.Relation.EQUAL_TO), td.totalHits);
      // timeout set but never fires
      s.setTimeout(() -> false);
      td = s.search(termQuery(), new TopScoreDocCollectorManager(10, Integer.MAX_VALUE));
      report("control: timeout never fires", td, s, NUM_DOCS_PER_SEG);
      assertEquals(new TotalHits(NUM_DOCS_PER_SEG, TotalHits.Relation.EQUAL_TO), td.totalHits);
      assertFalse(s.timedOut());
    }
  }

  /** User explicitly asks for exact counting (threshold = Integer.MAX_VALUE). */
  public void testTopScoreDocsExactCountRequested() throws Exception {
    try (Directory dir = buildIndex(1);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      s.setQueryCache(null);
      s.setTimeout(new CountingTimeout(1));
      TopDocs td = s.search(termQuery(), new TopScoreDocCollectorManager(10, Integer.MAX_VALUE));
      report("TopScoreDocCollectorManager(10, MAX_VALUE), TermQuery", td, s, NUM_DOCS_PER_SEG);
      assertTrue(s.timedOut());
      assertTrue(
          "TotalHits contract violated: " + td.totalHits + " but " + NUM_DOCS_PER_SEG + " match",
          contractHolds(td, NUM_DOCS_PER_SEG));
    }
  }

  /** Default IndexSearcher#search(Query,int) path (threshold 1000). */
  public void testSearchQueryIntDefaultPath() throws Exception {
    try (Directory dir = buildIndex(1);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      s.setQueryCache(null);
      s.setTimeout(new CountingTimeout(1));
      TopDocs td = s.search(conjunction(), 10);
      report("IndexSearcher#search(conjunction, 10)", td, s, NUM_DOCS_PER_SEG);
      assertTrue(s.timedOut());
      assertTrue(
          "TotalHits contract violated: " + td.totalHits + " but " + NUM_DOCS_PER_SEG + " match",
          contractHolds(td, NUM_DOCS_PER_SEG));
    }
  }

  /** Field sort path: IndexSearcher#search(Query,int,Sort). */
  public void testFieldSort() throws Exception {
    try (Directory dir = buildIndex(1);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      s.setQueryCache(null);
      s.setTimeout(new CountingTimeout(1));
      Sort sort = new Sort(new SortField("n", SortField.Type.LONG, true));
      TopDocs td = s.search(termQuery(), new TopFieldCollectorManager(sort, 10, Integer.MAX_VALUE));
      report("TopFieldCollectorManager(sort n desc, 10, MAX_VALUE)", td, s, NUM_DOCS_PER_SEG);
      assertTrue(s.timedOut());
      // Note: with the true count 500 and sort by n desc, the true top doc is n=499; a partial
      // collection of docs 0..99 returns n=99 as "top".
      System.out.println("    top sort value returned = " + ((org.apache.lucene.search.FieldDoc) td.scoreDocs[0]).fields[0]);
      assertTrue(
          "TotalHits contract violated: " + td.totalHits + " but " + NUM_DOCS_PER_SEG + " match",
          contractHolds(td, NUM_DOCS_PER_SEG));
    }
  }

  /** Concurrent: 3 segments, executor, one slice per segment; timeout after the first window. */
  public void testConcurrentMultiSegment() throws Exception {
    ExecutorService ex = Executors.newFixedThreadPool(3);
    try (Directory dir = buildIndex(3);
        DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals(3, r.leaves().size());
      IndexSearcher s =
          new IndexSearcher(r, ex) {
            @Override
            protected LeafSlice[] slices(java.util.List<org.apache.lucene.index.LeafReaderContext> leaves) {
              return slices(leaves, 1, 1, false); // one segment per slice
            }
          };
      s.setQueryCache(null);
      s.setTimeout(new CountingTimeout(1));
      TopDocs td = s.search(termQuery(), new TopScoreDocCollectorManager(10, Integer.MAX_VALUE));
      report("concurrent 3 slices, exact-count manager", td, s, 3 * NUM_DOCS_PER_SEG);
      assertTrue(s.timedOut());
      assertTrue(
          "TotalHits contract violated: " + td.totalHits + " but " + 3 * NUM_DOCS_PER_SEG + " match",
          contractHolds(td, 3 * NUM_DOCS_PER_SEG));
    } finally {
      ex.shutdown();
      ex.awaitTermination(10, TimeUnit.SECONDS);
    }
  }

  /**
   * Informational: what count() returns under the same timeout (int, no relation available). Doc
   * n=0 is deleted so that the Weight#count shortcuts return -1 and the BulkScorer actually runs.
   */
  public void testCountInformational() throws Exception {
    try (Directory dir = buildIndex(1)) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
        w.deleteDocuments(NumericDocValuesField.newSlowExactQuery("n", 0));
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        assertEquals(NUM_DOCS_PER_SEG - 1, r.numDocs());
        for (Query q : new Query[] {termQuery(), conjunction()}) {
          IndexSearcher s = new IndexSearcher(r);
          s.setQueryCache(null);
          s.setTimeout(new CountingTimeout(1));
          int c = s.count(q);
          System.out.println(
              String.format(
                  "%-58s count=%d timedOut()=%s trueMatches=%d",
                  "INFO count(" + q + ") with 1 deletion", c, s.timedOut(), NUM_DOCS_PER_SEG - 1));
        }
      }
    }
  }
}
