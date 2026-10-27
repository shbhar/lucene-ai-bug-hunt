package org.apache.lucene.search;

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.QueryTimeout;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestProbeReentryTimeout extends LuceneTestCase {
  static final class Counting implements QueryTimeout {
    final AtomicInteger calls = new AtomicInteger();
    final int limit;
    Counting(int limit) { this.limit = limit; }
    @Override public boolean shouldExit() { return calls.incrementAndGet() > limit; }
  }

  public void testSweep() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
        int x = 0;
        for (int s = 0; s < 3; s++) {
          for (int i = 0; i < 2; i++) {
            Document d = new Document();
            d.add(new KnnFloatVectorField("v", new float[] {x++, 1}, VectorSimilarityFunction.EUCLIDEAN));
            w.addDocument(d);
          }
          w.commit();
        }
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        System.out.println("leaves=" + r.leaves().size());
        IndexSearcher s = new IndexSearcher(r);
        KnnFloatVectorQuery q = new KnnFloatVectorQuery("v", new float[] {0, 1}, 10);
        Counting never = new Counting(Integer.MAX_VALUE);
        s.setTimeout(never);
        int full = s.count(q);
        int total = never.calls.get();
        System.out.println("no-timeout count=" + full + " shouldExit calls=" + total);
        StringBuilder sb = new StringBuilder();
        int prev = -1;
        boolean nonMonotone = false;
        for (int n = 0; n <= total + 1; n++) {
          s.setTimeout(new Counting(n));
          IndexSearcher fresh = new IndexSearcher(r);
          fresh.setTimeout(new Counting(n));
          int c = fresh.count(q);
          if (n == 9) {
            System.out.println("n=9 count=" + c + " timedOut()=" + fresh.timedOut());
            IndexSearcher f2 = new IndexSearcher(r); f2.setTimeout(new Counting(n));
            TopDocs td = f2.search(q, 10);
            System.out.println("n=9 search hits=" + td.scoreDocs.length + " timedOut()=" + f2.timedOut());
            IndexSearcher f3 = new IndexSearcher(r); f3.setTimeout(new Counting(n));
            System.out.println("n=9 rewrite=" + f3.rewrite(q));
          }
          sb.append(n).append(':').append(c).append(' ');
          if (prev > c) nonMonotone = true;
          prev = c;
        }
        System.out.println(sb);
        assertFalse("count decreased as the timeout allowed more work: " + sb, nonMonotone);
      }
    }
  }
}
