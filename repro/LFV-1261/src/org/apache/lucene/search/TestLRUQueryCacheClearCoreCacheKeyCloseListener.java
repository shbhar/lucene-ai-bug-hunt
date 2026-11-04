package org.apache.lucene.search;

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLRUQueryCacheClearCoreCacheKeyCloseListener extends LuceneTestCase {

  // Expected: after clearCoreCacheKey on an open core and re-caching, closing the reader runs this
  // cache's close listener once; actual: twice (a second listener was registered).
  public void testClearCoreCacheKeyThenRecacheRegistersOneCloseListener() throws Exception {
    AtomicInteger clearCalls = new AtomicInteger();
    LRUQueryCache cache =
        new LRUQueryCache(1000, 1 << 20, context -> true, Float.POSITIVE_INFINITY) {
          @Override
          public void clearCoreCacheKey(IndexReader.CacheKey coreKey) {
            clearCalls.incrementAndGet();
            super.clearCoreCacheKey(coreKey);
          }
        };
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new StringField("f", "a", Field.Store.NO));
        w.addDocument(doc);
      }
      DirectoryReader reader = DirectoryReader.open(dir);
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setQueryCache(cache);
      searcher.setQueryCachingPolicy(
          new QueryCachingPolicy() {
            @Override
            public void onUse(Query query) {}

            @Override
            public boolean shouldCache(Query query) {
              return true;
            }
          });
      Query query = new ConstantScoreQuery(new TermQuery(new Term("f", "a")));

      searcher.search(query, 1);
      cache.clearCoreCacheKey(reader.leaves().get(0).reader().getCoreCacheHelper().getKey());
      cache.cleanUp();
      searcher.search(query, 1);
      clearCalls.set(0);
      reader.close();
      assertEquals(1, clearCalls.get());
    }
  }
}
