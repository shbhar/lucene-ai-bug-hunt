import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.ImpactsEnum;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.SlowImpactsEnum;
import org.apache.lucene.index.StoredFieldVisitor;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermVectors;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorable;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;

/**
 * Independent check of: "BooleanScorer infinite loop on the last 4096-doc window of a segment".
 *
 * <p>Only public Lucene APIs are used. A real 2-doc index (doc0: f=a, doc1: f=b) is wrapped in a
 * FilterLeafReader that reports maxDoc = IndexWriter.MAX_DOCS and renumbers the two postings to
 * chosen doc ids. The query is a plain BooleanQuery of two SHOULD TermQuery clauses. The bug
 * tests assert the CORRECT behaviour, so on an affected Lucene they FAIL; the controls must PASS.
 */
@ThreadLeakScope(ThreadLeakScope.Scope.NONE) // a hung search thread cannot be stopped
public class VerifyBooleanScorerLastWindowTest extends LuceneTestCase {

  static final int MAX_DOCS = IndexWriter.MAX_DOCS; // 2_147_483_519
  static final int LAST_WINDOW_BASE = (int) ((1L << 31) - 4096); // 2_147_479_552
  static final long SETSCORER_BUDGET = 5_000_000L; // a 2-hit search needs a handful

  static Directory dir;
  static DirectoryReader base;

  @org.junit.BeforeClass
  public static void buildBase() throws IOException {
    dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      Document d0 = new Document();
      d0.add(new StringField("f", "a", Field.Store.NO));
      w.addDocument(d0);
      Document d1 = new Document();
      d1.add(new StringField("f", "b", Field.Store.NO));
      w.addDocument(d1);
      w.forceMerge(1);
    }
    base = DirectoryReader.open(dir);
    assertEquals(1, base.leaves().size());
    assertEquals(2, base.maxDoc());
  }

  @org.junit.AfterClass
  public static void closeBase() throws IOException {
    base.close();
    dir.close();
    base = null;
    dir = null;
  }

  // ---------------------------------------------------------------- helpers

  /** Postings enum whose doc ids are renumbered through map[realDoc]. */
  static final class MappedPostingsEnum extends PostingsEnum {
    final PostingsEnum in;
    final int[] map;
    int doc = -1;

    MappedPostingsEnum(PostingsEnum in, int[] map) {
      this.in = in;
      this.map = map;
    }

    @Override
    public int docID() {
      return doc;
    }

    @Override
    public int nextDoc() throws IOException {
      int d = in.nextDoc();
      doc = d == NO_MORE_DOCS ? NO_MORE_DOCS : map[d];
      return doc;
    }

    @Override
    public int advance(int target) throws IOException {
      while (doc < target) {
        nextDoc();
      }
      return doc;
    }

    @Override
    public long cost() {
      return in.cost();
    }

    @Override
    public int freq() throws IOException {
      return in.freq();
    }

    @Override
    public int nextPosition() throws IOException {
      return in.nextPosition();
    }

    @Override
    public int startOffset() throws IOException {
      return in.startOffset();
    }

    @Override
    public int endOffset() throws IOException {
      return in.endOffset();
    }

    @Override
    public org.apache.lucene.util.BytesRef getPayload() throws IOException {
      return in.getPayload();
    }
  }

  /** Leaf with maxDoc = bigMaxDoc and postings renumbered via map (must be increasing). */
  static final class RenumberedLeafReader extends FilterLeafReader {
    final int bigMaxDoc;
    final int[] map;

    RenumberedLeafReader(LeafReader in, int bigMaxDoc, int[] map) {
      super(in);
      for (int i = 1; i < map.length; i++) {
        if (map[i] <= map[i - 1]) throw new IllegalArgumentException("map must increase");
      }
      if (map[map.length - 1] >= bigMaxDoc) throw new IllegalArgumentException("doc >= maxDoc");
      this.bigMaxDoc = bigMaxDoc;
      this.map = map;
    }

    @Override
    public int maxDoc() {
      return bigMaxDoc;
    }

    @Override
    public int numDocs() {
      return bigMaxDoc; // no deletions
    }

    @Override
    public Bits getLiveDocs() {
      return null;
    }

    @Override
    public Terms terms(String field) {
      Terms t = super.terms(field);
      if (t == null) return null;
      final int[] m = map;
      return new FilterTerms(t) {
        @Override
        public TermsEnum iterator() throws IOException {
          return new FilterTermsEnum(in.iterator()) {
            @Override
            public PostingsEnum postings(PostingsEnum reuse, int flags) throws IOException {
              return new MappedPostingsEnum(in.postings(null, flags), m);
            }

            @Override
            public ImpactsEnum impacts(int flags) throws IOException {
              return new SlowImpactsEnum(postings(null, flags));
            }
          };
        }
      };
    }

    @Override
    public StoredFields storedFields() {
      return new StoredFields() {
        @Override
        public void document(int docID, StoredFieldVisitor visitor) {}
      };
    }

    @Override
    public TermVectors termVectors() {
      return TermVectors.EMPTY;
    }

    @Override
    public CacheHelper getCoreCacheHelper() {
      return null;
    }

    @Override
    public CacheHelper getReaderCacheHelper() {
      return null;
    }
  }

  static IndexSearcher searcher(int doc0, int doc1) {
    LeafReader leaf = base.leaves().get(0).reader();
    IndexSearcher s = new IndexSearcher(new RenumberedLeafReader(leaf, MAX_DOCS, new int[] {doc0, doc1}));
    s.setQueryCache(null);
    return s;
  }

  static Query aOrB() {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("f", "a")), Occur.SHOULD)
        .add(new TermQuery(new Term("f", "b")), Occur.SHOULD)
        .build();
  }

  /** Records collected docs; aborts after SETSCORER_BUDGET setScorer calls (no-progress probe). */
  static final class RecordingCollector implements Collector {
    final ScoreMode mode;
    final List<Integer> docs = new ArrayList<>();
    long setScorerCalls;
    String bulkScorerClass;

    RecordingCollector(ScoreMode mode) {
      this.mode = mode;
    }

    @Override
    public LeafCollector getLeafCollector(LeafReaderContext context) {
      return new LeafCollector() {
        @Override
        public void setScorer(Scorable scorer) {
          if (++setScorerCalls > SETSCORER_BUDGET) {
            throw new IllegalStateException(
                "no progress: " + setScorerCalls + " setScorer calls, collected=" + docs);
          }
        }

        @Override
        public void collect(int doc) {
          docs.add(doc);
        }
      };
    }

    @Override
    public ScoreMode scoreMode() {
      return mode;
    }
  }

  static RecordingCollector collect(IndexSearcher s, Query q, ScoreMode mode) throws IOException {
    final RecordingCollector c = new RecordingCollector(mode);
    try {
      s.search(
          q,
          new CollectorManager<RecordingCollector, Void>() {
            boolean created;

            @Override
            public RecordingCollector newCollector() {
              assertFalse("single leaf, single collector expected", created);
              created = true;
              return c;
            }

            @Override
            public Void reduce(Collection<RecordingCollector> collectors) {
              return null;
            }
          });
    } catch (IllegalStateException e) {
      throw new AssertionError(
          "search did not terminate: " + e.getMessage() + " (mode=" + mode + ")", e);
    }
    return c;
  }

  /** Runs s.count(q) in a daemon thread; fails with the hung thread's stack after timeoutMs. */
  static int countWithTimeout(IndexSearcher s, Query q, long timeoutMs) throws Exception {
    AtomicReference<Object> result = new AtomicReference<>();
    Thread t =
        new Thread(
            () -> {
              try {
                result.set(s.count(q));
              } catch (Throwable e) {
                result.set(e);
              }
            },
            "count-thread");
    t.setDaemon(true);
    t.start();
    t.join(timeoutMs);
    if (t.isAlive()) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 3; i++) { // three samples, 200ms apart
        sb.append("\n  sample ").append(i).append(':');
        StackTraceElement[] st = t.getStackTrace();
        for (int j = 0; j < Math.min(6, st.length); j++) sb.append("\n    at ").append(st[j]);
        Thread.sleep(200);
      }
      throw new AssertionError(
          "IndexSearcher.count did not return within " + timeoutMs + " ms; stack:" + sb);
    }
    Object r = result.get();
    if (r instanceof Throwable e) throw new AssertionError(e);
    return (Integer) r;
  }

  // ---------------------------------------------------------------- bug tests (fail on main)

  /** IndexSearcher.count of 'a OR b' when both hits are in the last 4096-doc window. */
  public void testBugCountLastWindow() throws Exception {
    IndexSearcher s = searcher(MAX_DOCS - 200, MAX_DOCS - 100);
    assertEquals(2, countWithTimeout(s, aOrB(), 20_000));
  }

  /** COMPLETE_NO_SCORES collection (counting / facets style). */
  public void testBugCompleteNoScoresLastWindow() throws Exception {
    IndexSearcher s = searcher(MAX_DOCS - 200, MAX_DOCS - 100);
    RecordingCollector c = collect(s, aOrB(), ScoreMode.COMPLETE_NO_SCORES);
    assertEquals(Arrays.asList(MAX_DOCS - 200, MAX_DOCS - 100), c.docs);
  }

  /** COMPLETE collection (scores needed, e.g. exhaustive scoring / sort with scores). */
  public void testBugCompleteLastWindow() throws Exception {
    IndexSearcher s = searcher(MAX_DOCS - 200, MAX_DOCS - 100);
    RecordingCollector c = collect(s, aOrB(), ScoreMode.COMPLETE);
    assertEquals(Arrays.asList(MAX_DOCS - 200, MAX_DOCS - 100), c.docs);
  }

  /** TOP_DOCS (sort by field without scores uses this mode). */
  public void testBugTopDocsLastWindow() throws Exception {
    IndexSearcher s = searcher(MAX_DOCS - 200, MAX_DOCS - 100);
    RecordingCollector c = collect(s, aOrB(), ScoreMode.TOP_DOCS);
    assertEquals(Arrays.asList(MAX_DOCS - 200, MAX_DOCS - 100), c.docs);
  }

  /** One hit early in the segment, one in the last window: first is collected, then the hang. */
  public void testBugMixedEarlyAndLastWindow() throws Exception {
    IndexSearcher s = searcher(1000, MAX_DOCS - 1);
    RecordingCollector c = collect(s, aOrB(), ScoreMode.COMPLETE_NO_SCORES);
    assertEquals(Arrays.asList(1000, MAX_DOCS - 1), c.docs);
  }

  /** Boundary: first doc id of the last window, 2^31 - 4096. */
  public void testBugBoundaryFirstDocOfLastWindow() throws Exception {
    IndexSearcher s = searcher(LAST_WINDOW_BASE, LAST_WINDOW_BASE + 1);
    RecordingCollector c = collect(s, aOrB(), ScoreMode.COMPLETE_NO_SCORES);
    assertEquals(Arrays.asList(LAST_WINDOW_BASE, LAST_WINDOW_BASE + 1), c.docs);
  }

  /** A QueryTimeout (TimeLimitingBulkScorer) does not rescue the search: it never returns. */
  public void testBugQueryTimeoutDoesNotStopIt() throws Exception {
    IndexSearcher s = searcher(MAX_DOCS - 200, MAX_DOCS - 100);
    final long deadline = System.nanoTime() + 1_000_000_000L; // 1 s budget
    s.setTimeout(() -> System.nanoTime() > deadline);
    // Correct behaviour: returns 2, or returns early with a timed-out partial result.
    // Either way it must come back long before 20 s.
    int c = countWithTimeout(s, aOrB(), 20_000);
    assertTrue(c == 2 || c < 2);
  }

  // ---------------------------------------------------------------- controls (pass on main)

  /** Same docs, one window lower (still near MAX_DOCS): BooleanScorer works. */
  public void testControlOneWindowLower() throws Exception {
    IndexSearcher s = searcher(MAX_DOCS - 200 - 4096, MAX_DOCS - 100 - 4096);
    assertEquals(2, countWithTimeout(s, aOrB(), 20_000));
    for (ScoreMode m :
        new ScoreMode[] {ScoreMode.COMPLETE_NO_SCORES, ScoreMode.COMPLETE, ScoreMode.TOP_DOCS}) {
      RecordingCollector c = collect(s, aOrB(), m);
      assertEquals(m.toString(), Arrays.asList(MAX_DOCS - 4296, MAX_DOCS - 4196), c.docs);
    }
  }

  /** Boundary: last doc id of the second-to-last window, 2^31 - 4097. */
  public void testControlBoundaryLastDocOfPreviousWindow() throws Exception {
    IndexSearcher s = searcher(LAST_WINDOW_BASE - 2, LAST_WINDOW_BASE - 1);
    RecordingCollector c = collect(s, aOrB(), ScoreMode.COMPLETE_NO_SCORES);
    assertEquals(Arrays.asList(LAST_WINDOW_BASE - 2, LAST_WINDOW_BASE - 1), c.docs);
  }

  /** TOP_SCORES (default top-k search) uses MaxScoreBulkScorer, which is guarded: works. */
  public void testControlTopScoresLastWindow() throws Exception {
    IndexSearcher s = searcher(MAX_DOCS - 200, MAX_DOCS - 100);
    TopDocs td = s.search(aOrB(), 10);
    int[] got = Arrays.stream(td.scoreDocs).mapToInt(sd -> sd.doc).sorted().toArray();
    assertArrayEquals(new int[] {MAX_DOCS - 200, MAX_DOCS - 100}, got);
    RecordingCollector c = collect(s, aOrB(), ScoreMode.TOP_SCORES);
    assertEquals(Arrays.asList(MAX_DOCS - 200, MAX_DOCS - 100), c.docs);
  }

  /** Single-clause queries on the same reader (no BooleanScorer): work. */
  public void testControlSingleTermLastWindow() throws Exception {
    IndexSearcher s = searcher(MAX_DOCS - 200, MAX_DOCS - 100);
    RecordingCollector c =
        collect(s, new TermQuery(new Term("f", "b")), ScoreMode.COMPLETE_NO_SCORES);
    assertEquals(Arrays.asList(MAX_DOCS - 100), c.docs);
    c = collect(s, new TermQuery(new Term("f", "a")), ScoreMode.COMPLETE);
    assertEquals(Arrays.asList(MAX_DOCS - 200), c.docs);
  }

  /** The renumbering wrapper itself is sound: low doc ids, all modes. */
  public void testControlLowDocIds() throws Exception {
    IndexSearcher s = searcher(5, 9000);
    assertEquals(2, countWithTimeout(s, aOrB(), 20_000));
    RecordingCollector c = collect(s, aOrB(), ScoreMode.COMPLETE);
    assertEquals(Arrays.asList(5, 9000), c.docs);
    ScoreDoc[] sd = s.search(aOrB(), 10).scoreDocs;
    assertEquals(2, sd.length);
  }
}
