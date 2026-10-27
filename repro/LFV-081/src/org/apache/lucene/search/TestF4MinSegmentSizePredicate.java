package org.apache.lucene.search;

import java.util.function.Predicate;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * drive LRUQueryCache.MinSegmentSizePredicate (LRUQueryCache.java:278-296,
 * {@code return maxDoc * 2 > averageTotalDocs;} at :294) directly with FilterLeafReaders that
 * report a large maxDoc. The javadoc of LRUQueryCache(int,long) (:242-249) promises caching on
 * "leaves that have more than 10k documents and have more than half of the average documents per
 * leave of the index".
 */
public class TestF4MinSegmentSizePredicate extends LuceneTestCase {

  /** A leaf over a real 1-doc segment that reports a different maxDoc. */
  static final class BigMaxDocReader extends FilterLeafReader {
    final int fakeMaxDoc;

    BigMaxDocReader(LeafReader in, int fakeMaxDoc) {
      super(in);
      this.fakeMaxDoc = fakeMaxDoc;
    }

    @Override
    public int maxDoc() {
      return fakeMaxDoc;
    }

    @Override
    public CacheHelper getCoreCacheHelper() {
      return in.getCoreCacheHelper();
    }

    @Override
    public CacheHelper getReaderCacheHelper() {
      return null;
    }
  }

  /** what the javadoc describes, evaluated without overflow */
  static boolean intended(LeafReaderContext ctx, int minSize) {
    int maxDoc = ctx.reader().maxDoc();
    if (maxDoc < minSize) return false;
    IndexReader top = org.apache.lucene.index.ReaderUtil.getTopLevelContext(ctx).reader();
    long avg = top.maxDoc() / top.leaves().size();
    return 2L * maxDoc > avg;
  }

  private Directory dir;
  private DirectoryReader real;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document d = new Document();
      d.add(new StringField("id", "x", Field.Store.NO));
      w.addDocument(d);
    }
    real = DirectoryReader.open(dir);
  }

  @Override
  public void tearDown() throws Exception {
    real.close();
    dir.close();
    super.tearDown();
  }

  LeafReader leaf(int fakeMaxDoc) {
    return new BigMaxDocReader(real.leaves().get(0).reader(), fakeMaxDoc);
  }

  /** single-leaf index: the predicate must accept the only (huge) segment */
  public void testSingleLeafThreshold() throws Exception {
    Predicate<LeafReaderContext> p = new LRUQueryCache.MinSegmentSizePredicate(10000);
    int[] sizes = {10_000, 1 << 29, (1 << 30) - 1, 1 << 30, (1 << 30) + 1, 2_000_000_000, 2147483519};
    StringBuilder bad = new StringBuilder();
    for (int n : sizes) {
      LeafReader r = leaf(n);
      boolean got = p.test(r.getContext());
      boolean want = intended(r.getContext(), 10000);
      System.out.println("[single leaf] maxDoc=" + n + " predicate=" + got + " intended=" + want);
      if (got != want) bad.append(n).append(' ');
    }
    assertEquals("sizes where MinSegmentSizePredicate disagrees with its javadoc", "", bad.toString());
  }

  /** two leaves: a 2^30-doc leaf and a 1000-doc leaf */
  public void testTwoLeaves() throws Exception {
    Predicate<LeafReaderContext> p = new LRUQueryCache.MinSegmentSizePredicate(10000);
    try (MultiReader mr = new MultiReader(new IndexReader[] {leaf(1 << 30), leaf(20_000)}, false)) {
      StringBuilder bad = new StringBuilder();
      for (LeafReaderContext ctx : mr.leaves()) {
        boolean got = p.test(ctx);
        boolean want = intended(ctx, 10000);
        System.out.println("[two leaves] leaf maxDoc=" + ctx.reader().maxDoc() + " predicate=" + got + " intended=" + want);
        if (got != want) bad.append(ctx.reader().maxDoc()).append(' ');
      }
      assertEquals("", bad.toString());
    }
  }

  /** end to end: LRUQueryCache(int,long) with a RAM budget large enough to pass the size gate */
  public void testEndToEndCacheCount() throws Exception {
    StringBuilder bad = new StringBuilder();
    for (int n : new int[] {(1 << 30) - 1, 1 << 30}) {
      // RAM gate (LRUQueryCache.java:792-801): maxDoc/8*5 < maxRam/16  -> 64 GB budget passes
      LRUQueryCache cache = new LRUQueryCache(100, 64L << 30);
      LeafReader r = leaf(n);
      IndexSearcher s = new IndexSearcher(r);
      s.setQueryCache(cache);
      s.setQueryCachingPolicy(
          new QueryCachingPolicy() {
            @Override
            public void onUse(Query query) {}

            @Override
            public boolean shouldCache(Query query) {
              return true;
            }
          });
      Query q = new ConstantScoreQuery(new TermQuery(new Term("id", "x")));
      int hits1 = s.search(q, 1).scoreDocs.length;
      int hits2 = s.search(q, 1).scoreDocs.length;
      System.out.println("[e2e] maxDoc=" + n + " hits=" + hits1 + "/" + hits2 + " cacheCount=" + cache.getCacheCount()
          + " hitCount=" + cache.getHitCount() + " missCount=" + cache.getMissCount());
      if (cache.getCacheCount() != 1) bad.append(n).append(' ');
      cache.close();
    }
    assertEquals("maxDoc values whose segment was never cached", "", bad.toString());
  }
}
