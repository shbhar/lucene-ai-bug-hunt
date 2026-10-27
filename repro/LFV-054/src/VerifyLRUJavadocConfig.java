import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCache;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.UsageTrackingQueryCachingPolicy;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent verification (search-concurrency-model-5): the exact configuration from the
 * LRUQueryCache class javadoc (maxSize 256, 50 MB, 2-arg constructor, UsageTrackingQueryCachingPolicy)
 * and an ordinary top-10 search with a range FILTER clause, on an index whose segments all pass the
 * default MinSegmentSizePredicate(10000). API common to 10.5.2 and main.
 */
public class VerifyLRUJavadocConfig extends LuceneTestCase {

  static Directory build(int segments, int docsPerSegment) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setRAMBufferSizeMB(256)
            .setMaxBufferedDocs(docsPerSegment + 1);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      int id = 0;
      for (int s = 0; s < segments; s++) {
        for (int d = 0; d < docsPerSegment; d++, id++) {
          Document doc = new Document();
          doc.add(new StringField("f", "x", Field.Store.NO));
          doc.add(new IntPoint("v", d)); // same distribution in each segment
          w.addDocument(doc);
        }
        w.flush();
      }
    }
    return dir;
  }

  static long[] run(int segments, int searches, String label) throws IOException {
    try (Directory dir = build(segments, 10_000);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      final int maxNumberOfCachedQueries = 256; // verbatim from the class javadoc
      final long maxRamBytesUsed = 50 * 1024L * 1024L; // 50MB, verbatim
      final LRUQueryCache queryCache =
          new LRUQueryCache(maxNumberOfCachedQueries, maxRamBytesUsed);
      final QueryCachingPolicy defaultCachingPolicy = new UsageTrackingQueryCachingPolicy();
      IndexSearcher indexSearcher = new IndexSearcher(reader);
      indexSearcher.setQueryCache((QueryCache) queryCache);
      indexSearcher.setQueryCachingPolicy(defaultCachingPolicy);

      Query q =
          new BooleanQuery.Builder()
              .add(new TermQuery(new Term("f", "x")), BooleanClause.Occur.MUST)
              .add(IntPoint.newRangeQuery("v", 0, 4_999), BooleanClause.Occur.FILTER)
              .build();
      long hitsLast = 0;
      long builtLast = 0;
      long t0 = 0, t1 = 0;
      for (int i = 0; i < searches; i++) {
        long h0 = queryCache.getHitCount();
        long c0 = queryCache.getCacheCount();
        if (i == searches / 2) t0 = System.nanoTime();
        TopDocs td = indexSearcher.search(q, 10);
        assertEquals(10, td.scoreDocs.length);
        hitsLast = queryCache.getHitCount() - h0;
        builtLast = queryCache.getCacheCount() - c0;
      }
      t1 = System.nanoTime();
      System.out.println(
          label
              + ": segments="
              + reader.leaves().size()
              + " maxDoc="
              + reader.maxDoc()
              + " searches="
              + searches
              + " -> cacheSize="
              + queryCache.getCacheSize()
              + " cacheCount(ever)="
              + queryCache.getCacheCount()
              + " evictions="
              + queryCache.getEvictionCount()
              + " hitsInLastSearch="
              + hitsLast
              + " docIdSetsBuiltInLastSearch="
              + builtLast
              + " totalHits="
              + queryCache.getHitCount()
              + " ramBytesUsed="
              + queryCache.ramBytesUsed()
              + " (indicative ms for 2nd half="
              + (t1 - t0) / 1_000_000
              + ")");
      return new long[] {hitsLast, queryCache.getCacheSize()};
    }
  }

  public void testJavadocConfig12Segments() throws IOException {
    long[] r = run(12, 40, "L javadoc cfg");
    assertEquals(12, r[0]);
  }

  public void testJavadocConfig20Segments() throws IOException {
    long[] r = run(20, 40, "L javadoc cfg");
    assertEquals("cache hits on the last of 40 identical searches", 20, r[0]);
  }

  public void testJavadocConfig34Segments() throws IOException {
    // 34 equal segments: the GITHUB#12140 shape (34 x 5GB segments)
    long[] r = run(34, 40, "L javadoc cfg");
    assertEquals("cache hits on the last of 40 identical searches", 34, r[0]);
  }
}
