package org.apache.lucene.sandbox.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestQueryProfilerNestedRewriteTime extends LuceneTestCase {

  public void testRewriteTimeOfFilteredKnnQueryIsAtMostTwiceTheSearchTime() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new StringField("color", "red", Field.Store.NO));
        doc.add(new KnnFloatVectorField("v", new float[] {0f, 1f}));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        QueryProfilerIndexSearcher searcher = new QueryProfilerIndexSearcher(reader);
        Query query =
            new KnnFloatVectorQuery(
                "v", new float[] {0f, 1f}, 1, new TermQuery(new Term("color", "red")));
        long start = System.nanoTime();
        searcher.search(query, 1);
        long searchTime = System.nanoTime() - start;
        long rewriteTime = searcher.getRewriteTime();
        // expected: at most 2 * searchTime; actual: about System.nanoTime() (-ea: AssertionError)
        assertTrue(
            "rewrite time " + rewriteTime + " ns, but the whole search took " + searchTime + " ns",
            rewriteTime <= 2 * searchTime);
      }
    }
  }
}
