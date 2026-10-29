package org.apache.lucene.queries.function;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queries.function.valuesource.IntFieldSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFunctionRangeQueryExclusiveLimit extends LuceneTestCase {

  public void testExclusiveLowerBoundAtIntMax() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("n", Integer.MAX_VALUE));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query q =
            new FunctionRangeQuery(
                new IntFieldSource("n"), String.valueOf(Integer.MAX_VALUE), null, false, true);
        // n > Integer.MAX_VALUE matches nothing: expected 0 hits, actual 1
        assertEquals(q.toString(), 0, new IndexSearcher(reader).count(q));
      }
    }
  }
}
