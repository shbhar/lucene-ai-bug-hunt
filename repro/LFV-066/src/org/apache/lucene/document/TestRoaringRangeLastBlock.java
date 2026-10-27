package org.apache.lucene.document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValuesSkipper;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.BulkScorer;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.RoaringDocIdSet;

/**
 * Independent check of RoaringDocIdSet.Builder#add(int,int) in the last 2^16-doc block (block
 * 32767, docs >= 2^31 - 2^16 = 2,147,418,112). Lives in org.apache.lucene.document only so it can
 * construct the package-private RangeBulkScorer that production uses for sorted-index ranges.
 *
 * <p>Every "bug" test asserts the documented contract ("Equivalent to calling add(int) for each min
 * <= doc < max") and therefore FAILS if the bug is present. Every "control" test must PASS.
 */
public class TestRoaringRangeLastBlock extends LuceneTestCase {

  static final int MAX_DOCS = IndexWriter.MAX_DOCS; // 2^31 - 128, documented hard limit
  static final int LAST_BLOCK_START = 32767 << 16; // 2,147,418,112
  static final int SMALLER_MAXDOC = LAST_BLOCK_START; // 2^31 - 2^16: last block is 32766

  // ---------------------------------------------------------------- helpers

  static int[] toArray(DocIdSetIterator it) throws IOException {
    List<Integer> l = new ArrayList<>();
    for (int d = it.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = it.nextDoc()) {
      l.add(d);
    }
    int[] a = new int[l.size()];
    for (int i = 0; i < a.length; i++) a[i] = l.get(i);
    return a;
  }

  static int[] expected(int min, int max) {
    int[] a = new int[max - min];
    for (int i = 0; i < a.length; i++) a[i] = min + i;
    return a;
  }

  /** Reference: the per-doc path that the javadoc says add(min,max) is equivalent to. */
  static RoaringDocIdSet perDoc(int maxDoc, int min, int max) {
    RoaringDocIdSet.Builder b = new RoaringDocIdSet.Builder(maxDoc);
    for (int d = min; d < max; d++) b.add(d);
    return b.build();
  }

  /** Runs add(min,max) and compares with the per-doc reference. */
  static void checkRange(int maxDoc, int min, int max) throws IOException {
    RoaringDocIdSet ref = perDoc(maxDoc, min, max);
    assertArrayEquals("per-doc reference itself", expected(min, max), toArray(ref.iterator()));
    RoaringDocIdSet.Builder b = new RoaringDocIdSet.Builder(maxDoc);
    try {
      b.add(min, max);
    } catch (Throwable t) {
      throw new AssertionError(
          "add(" + min + ", " + max + ") with maxDoc=" + maxDoc + " threw " + t, t);
    }
    RoaringDocIdSet got = b.build();
    assertEquals(ref.cardinality(), got.cardinality());
    assertArrayEquals(toArray(ref.iterator()), toArray(got.iterator()));
  }

  // ---------------------------------------------------------------- 1. Builder level

  /** BUG: a 10-doc range at the very end of a MAX_DOCS segment. */
  public void testBugTenDocsAtEndOfMaxDocsSegment() throws IOException {
    checkRange(MAX_DOCS, MAX_DOCS - 10, MAX_DOCS);
  }

  /** BUG: the whole last block (65,407 docs, dense-bitset branch). */
  public void testBugWholeLastBlock() throws IOException {
    checkRange(MAX_DOCS, LAST_BLOCK_START, MAX_DOCS);
  }

  /** BUG: a 200-doc range that crosses from block 32766 into block 32767. */
  public void testBugRangeCrossingIntoLastBlock() throws IOException {
    checkRange(MAX_DOCS, LAST_BLOCK_START - 100, LAST_BLOCK_START + 100);
  }

  /** BUG: mixing add(int) and add(int,int) inside block 32767. */
  public void testBugMixedSingleThenRangeInLastBlock() throws IOException {
    RoaringDocIdSet.Builder b = new RoaringDocIdSet.Builder(MAX_DOCS);
    b.add(MAX_DOCS - 50);
    try {
      b.add(MAX_DOCS - 10, MAX_DOCS);
    } catch (Throwable t) {
      throw new AssertionError("add(MAX_DOCS-10, MAX_DOCS) after add(MAX_DOCS-50) threw " + t, t);
    }
    int[] exp = new int[11];
    exp[0] = MAX_DOCS - 50;
    for (int i = 0; i < 10; i++) exp[i + 1] = MAX_DOCS - 10 + i;
    assertArrayEquals(exp, toArray(b.build().iterator()));
  }

  /** CONTROL: the per-doc add(int) path handles the same docs of block 32767 correctly. */
  public void testControlPerDocInLastBlock() throws IOException {
    assertArrayEquals(
        expected(MAX_DOCS - 10, MAX_DOCS), toArray(perDoc(MAX_DOCS, MAX_DOCS - 10, MAX_DOCS).iterator()));
    RoaringDocIdSet whole = perDoc(MAX_DOCS, LAST_BLOCK_START, MAX_DOCS);
    assertEquals(MAX_DOCS - LAST_BLOCK_START, whole.cardinality());
  }

  /** CONTROL: same shape of range, but the segment ends at 2^31-2^16 (last block = 32766). */
  public void testControlSmallerSegment() throws IOException {
    checkRange(SMALLER_MAXDOC, SMALLER_MAXDOC - 10, SMALLER_MAXDOC);
    checkRange(SMALLER_MAXDOC, SMALLER_MAXDOC - 65536, SMALLER_MAXDOC);
  }

  /** CONTROL: in a MAX_DOCS builder, a range ending exactly at the start of block 32767. */
  public void testControlRangeEndingAtLastBlockStart() throws IOException {
    checkRange(MAX_DOCS, LAST_BLOCK_START - 10, LAST_BLOCK_START);
  }

  // ---------------------------------------------------------------- 2. LRUQueryCache.cacheImpl

  /** Exposes the protected default cacheImpl so it can be fed a real RangeBulkScorer. */
  static final class ExposedCache extends LRUQueryCache {
    ExposedCache() {
      super(10, 1L << 20);
    }

    int[] cacheAndList(BulkScorer s, int maxDoc) throws IOException {
      CacheAndCount cc = cacheImpl(s, maxDoc);
      int[] a = toArray(cc.iterator());
      assertEquals(a.length, cc.count());
      return a;
    }
  }

  static BulkScorer rangeBulkScorer(int min, int max) {
    // exactly what SortedSkipperScorerSupplier#bulkScorer builds
    return new RangeBulkScorer(DocIdSetIterator.range(min, max), 1f, min, max);
  }

  /** BUG: default cacheImpl (roaring branch, density < 1%) on a sorted-range bulk scorer. */
  public void testBugCacheImplLastBlock() throws IOException {
    ExposedCache c = new ExposedCache();
    int[] got;
    try {
      got = c.cacheAndList(rangeBulkScorer(MAX_DOCS - 10, MAX_DOCS), MAX_DOCS);
    } catch (Throwable t) {
      throw new AssertionError("LRUQueryCache.cacheImpl threw " + t, t);
    }
    assertArrayEquals(expected(MAX_DOCS - 10, MAX_DOCS), got);
  }

  /** CONTROL: same call, segment of 2^31-2^16 docs. */
  public void testControlCacheImplSmallerSegment() throws IOException {
    ExposedCache c = new ExposedCache();
    assertArrayEquals(
        expected(SMALLER_MAXDOC - 10, SMALLER_MAXDOC),
        c.cacheAndList(rangeBulkScorer(SMALLER_MAXDOC - 10, SMALLER_MAXDOC), SMALLER_MAXDOC));
  }

  // ---------------------------------------------------------------- 3. end-to-end IndexSearcher

  /**
   * A real 1-doc index sorted on "ts" with a doc-values skip index, viewed through a FilterLeafReader
   * that pretends the segment has {@code fakeMaxDoc} docs where doc d has ts == d. Only maxDoc,
   * numDocs, the numeric doc values and the skipper of "ts" are faked; FieldInfos, index sort and
   * the core cache key are the real ones.
   */
  static final class BigSortedLeaf extends FilterLeafReader {
    final int fakeMaxDoc;

    BigSortedLeaf(LeafReader in, int fakeMaxDoc) {
      super(in);
      this.fakeMaxDoc = fakeMaxDoc;
    }

    @Override
    public int maxDoc() {
      return fakeMaxDoc;
    }

    @Override
    public int numDocs() {
      return fakeMaxDoc;
    }

    @Override
    public NumericDocValues getNumericDocValues(String field) {
      if (!"ts".equals(field)) return null;
      final int n = fakeMaxDoc;
      return new NumericDocValues() {
        int doc = -1;

        @Override
        public long longValue() {
          return doc;
        }

        @Override
        public boolean advanceExact(int target) {
          doc = target;
          return true;
        }

        @Override
        public int docID() {
          return doc;
        }

        @Override
        public int nextDoc() {
          return advance(doc + 1);
        }

        @Override
        public int advance(int target) {
          return doc = target >= n ? NO_MORE_DOCS : target;
        }

        @Override
        public long cost() {
          return n;
        }
      };
    }

    @Override
    public DocValuesSkipper getDocValuesSkipper(String field) {
      if (!"ts".equals(field)) return null;
      final int n = fakeMaxDoc;
      final int interval = 4096;
      return new DocValuesSkipper() {
        int lo = -1, hi = -1;

        @Override
        public void advance(int target) {
          if (target >= n) {
            lo = hi = DocIdSetIterator.NO_MORE_DOCS;
          } else {
            lo = (target / interval) * interval;
            hi = (int) Math.min((long) lo + interval - 1, n - 1L);
          }
        }

        @Override
        public int numLevels() {
          return 1;
        }

        @Override
        public int minDocID(int level) {
          return lo;
        }

        @Override
        public int maxDocID(int level) {
          return hi;
        }

        @Override
        public long minValue(int level) {
          return lo;
        }

        @Override
        public long maxValue(int level) {
          return hi;
        }

        @Override
        public int docCount(int level) {
          return hi - lo + 1;
        }

        @Override
        public long minValue() {
          return 0;
        }

        @Override
        public long maxValue() {
          return n - 1L;
        }

        @Override
        public int docCount() {
          return n;
        }
      };
    }

    @Override
    public CacheHelper getCoreCacheHelper() {
      return in.getCoreCacheHelper();
    }

    @Override
    public CacheHelper getReaderCacheHelper() {
      return in.getReaderCacheHelper();
    }
  }

  enum CacheMode {
    NONE,
    /** LRUQueryCache(maxSize, maxRam): 16 partitions, default MinSegmentSizePredicate(10000). */
    DEFAULT_PREDICATE,
    /** LRUQueryCache(maxSize, maxRam, ctx -> true, 10f): 16 partitions, cache every leaf. */
    ALL_LEAVES
  }

  /** Result of one count: the hit count and how many entries ended up in the query cache. */
  record Result(int count, long cacheCount) {}

  /**
   * Counts docs with ts in [lo, hi] on a fake segment of {@code fakeMaxDoc} docs. With a cache,
   * maxRamBytesUsed is set so that the worst-case-size gate (maxDoc/8*5 < maxRam/numberOfPartitions,
   * i.e. ~1.35 GB per partition for a MAX_DOCS segment) passes; it is only a budget, nothing is
   * allocated up front.
   */
  static Result countOnBigSegment(int fakeMaxDoc, long lo, long hi, CacheMode mode)
      throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setCodec(TestUtil.getDefaultCodec());
      iwc.setIndexSort(new Sort(new SortField("ts", SortField.Type.LONG)));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document d = new Document();
        d.add(NumericDocValuesField.indexedField("ts", 0L));
        w.addDocument(d);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReader leaf = new BigSortedLeaf(r.leaves().get(0).reader(), fakeMaxDoc);
        IndexSearcher s = new IndexSearcher(leaf);
        LRUQueryCache cache =
            switch (mode) {
              case NONE -> null;
              case DEFAULT_PREDICATE -> new LRUQueryCache(1000, 24L << 30);
              case ALL_LEAVES -> new LRUQueryCache(1000, 24L << 30, ctx -> true, 10f);
            };
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
        Query q = NumericDocValuesField.newSlowRangeQuery("ts", lo, hi);
        int n = s.count(q);
        return new Result(n, cache == null ? 0 : cache.getCacheCount());
      }
    }
  }

  /** CONTROL: no query cache -> the same query on the same fake segment counts 10. */
  public void testControlEndToEndNoCache() throws IOException {
    assertEquals(new Result(10, 0), countOnBigSegment(MAX_DOCS, MAX_DOCS - 10, MAX_DOCS - 1, CacheMode.NONE));
  }

  /**
   * OBSERVATION (not the bug under test): with the default leavesToCache predicate, a MAX_DOCS
   * single-segment index is never cached, because MinSegmentSizePredicate computes maxDoc * 2 in
   * int (wraps negative for maxDoc >= 2^30). So the default-constructed LRUQueryCache does not reach
   * the bug; the count is right and the cache stays empty.
   */
  public void testObservationDefaultPredicateNeverCachesHugeSegment() throws IOException {
    assertEquals(
        new Result(10, 0),
        countOnBigSegment(MAX_DOCS, MAX_DOCS - 10, MAX_DOCS - 1, CacheMode.DEFAULT_PREDICATE));
  }

  /**
   * OBSERVATION (side finding, performance only): the default predicate's threshold is exactly 2^30.
   * A single-segment index of 2^30-1 docs is cached, one of 2^30 docs is not, although the javadoc of
   * LRUQueryCache(int,long) says leaves with more than 10k docs and more than half the average leaf
   * size are cached. Ranges here are far below block 32767, so the Roaring bug is not involved.
   */
  public void testObservationDefaultPredicateThresholdIs2pow30() throws IOException {
    int below = (1 << 30) - 1, at = 1 << 30;
    assertEquals(new Result(10, 1), countOnBigSegment(below, below - 10, below - 1, CacheMode.DEFAULT_PREDICATE));
    assertEquals(new Result(10, 0), countOnBigSegment(at, at - 10, at - 1, CacheMode.DEFAULT_PREDICATE));
    // same segment of 2^30 docs IS cached when the predicate accepts every leaf
    assertEquals(new Result(10, 1), countOnBigSegment(at, at - 10, at - 1, CacheMode.ALL_LEAVES));
  }

  /** CONTROL (non-vacuous: the entry IS cached): segment of 2^31-2^16 docs, last 10 docs. */
  public void testControlEndToEndCacheSmallerSegment() throws IOException {
    assertEquals(
        new Result(10, 1),
        countOnBigSegment(SMALLER_MAXDOC, SMALLER_MAXDOC - 10, SMALLER_MAXDOC - 1, CacheMode.ALL_LEAVES));
  }

  /** CONTROL (non-vacuous): MAX_DOCS segment, 10 docs ending just before block 32767. */
  public void testControlEndToEndCacheBeforeLastBlock() throws IOException {
    assertEquals(
        new Result(10, 1),
        countOnBigSegment(MAX_DOCS, LAST_BLOCK_START - 10, LAST_BLOCK_START - 1, CacheMode.ALL_LEAVES));
  }

  /** BUG: cache every leaf, MAX_DOCS segment, last 10 docs (ts in [MAX_DOCS-10, MAX_DOCS-1]). */
  public void testBugEndToEndCacheLastBlock() throws IOException {
    Result res;
    try {
      res = countOnBigSegment(MAX_DOCS, MAX_DOCS - 10, MAX_DOCS - 1, CacheMode.ALL_LEAVES);
    } catch (Throwable t) {
      throw new AssertionError("IndexSearcher.count with LRUQueryCache threw " + t, t);
    }
    assertEquals(new Result(10, 1), res);
  }
}
