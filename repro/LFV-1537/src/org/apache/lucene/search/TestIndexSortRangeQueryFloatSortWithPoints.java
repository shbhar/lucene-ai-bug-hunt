package org.apache.lucene.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIndexSortRangeQueryFloatSortWithPoints extends LuceneTestCase {

  public void testFloatSortedIndexWithPointsDelegatesToFallback() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setIndexSort(new Sort(new SortField("f", SortField.Type.FLOAT)));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        doc.add(new FloatPoint("f", -1f));
        doc.add(new FloatDocValuesField("f", -1f));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        long bits = Float.floatToRawIntBits(-1f);
        Query fallback = NumericDocValuesField.newSlowExactQuery("f", bits);
        Query query = new IndexSortSortedNumericDocValuesRangeQuery("f", bits, bits, fallback);
        // expected: 1, the fallback's count (the index sort is FLOAT, not INT or LONG); actual: 0
        assertEquals(searcher.count(fallback), searcher.count(query));
      }
    }
  }
}
