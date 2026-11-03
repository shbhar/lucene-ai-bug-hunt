package org.apache.lucene.index;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIndexSortAddIndexesEmptyReader extends LuceneTestCase {

  public void testEmptyReaderDoesNotShiftDocIds() throws Exception {
    Sort sort = new Sort(new SortField("k", SortField.Type.LONG));
    try (Directory src = newDirectory();
        Directory dst = newDirectory()) {
      try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig().setIndexSort(sort))) {
        for (String id : new String[] {"a", "c"}) {
          Document doc = new Document();
          doc.add(new StringField("id", id, Field.Store.YES));
          doc.add(new NumericDocValuesField("k", id.equals("a") ? 1 : 2));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader full = DirectoryReader.open(src);
          IndexWriter w = new IndexWriter(dst, new IndexWriterConfig().setIndexSort(sort))) {
        CodecReader empty =
            SortingCodecReader.wrap(SlowCodecReaderWrapper.wrap(new ParallelLeafReader()), sort);
        w.addIndexes((CodecReader) full.leaves().get(0).reader(), empty);
      }
      try (DirectoryReader r = DirectoryReader.open(dst)) {
        IndexSearcher s = new IndexSearcher(r);
        TopDocs hits = s.search(new TermQuery(new Term("id", "a")), 1);
        // expected: id:a hits the doc storing "a"; actual: it hits the doc storing "c" (doc ids shifted by the empty reader)
        assertEquals("a", s.storedFields().document(hits.scoreDocs[0].doc).get("id"));
      }
    }
  }
}
