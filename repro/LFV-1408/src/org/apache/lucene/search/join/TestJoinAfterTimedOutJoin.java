package org.apache.lucene.search.join;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestJoinAfterTimedOutJoin extends LuceneTestCase {

  public void testJoinIsNotServedTheTimedOutJoinsCacheEntry() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document from = new Document();
      from.add(new StringField("type", "from", Field.Store.NO));
      from.add(new SortedDocValuesField("fk", new BytesRef("a")));
      w.addDocument(from);
      Document to = new Document();
      to.add(new StringField("tk", "a", Field.Store.NO));
      w.addDocument(to);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        LRUQueryCache cache = new LRUQueryCache(100, 1 << 20, leaf -> true, Float.POSITIVE_INFINITY);
        QueryCachingPolicy always =
            new QueryCachingPolicy() {
              @Override
              public void onUse(Query query) {}

              @Override
              public boolean shouldCache(Query query) {
                return true;
              }
            };
        Query fromQuery = new TermQuery(new Term("type", "from"));

        IndexSearcher timedOut = new IndexSearcher(reader);
        timedOut.setQueryCache(cache);
        timedOut.setQueryCachingPolicy(always);
        timedOut.setTimeout(() -> true);
        timedOut.count(
            JoinUtil.createJoinQuery("fk", false, "tk", fromQuery, timedOut, ScoreMode.None));

        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(cache);
        searcher.setQueryCachingPolicy(always);
        Query join = JoinUtil.createJoinQuery("fk", false, "tk", fromQuery, searcher, ScoreMode.None);
        // Expected 1 (no timeout on this searcher); actual 0, served from the timed-out join's cache entry.
        assertEquals(1, searcher.count(join));
      }
    }
  }
}
