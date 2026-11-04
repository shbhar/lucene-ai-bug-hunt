package org.apache.lucene.document;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestLatLonPointSetLocationAfterBytesSlice extends LuceneTestCase {

  public void testSetLocationValueAfterSlicedBytesValue() throws Exception {
    LatLonPoint field = new LatLonPoint("p", 10, 20);
    field.setBytesValue(new BytesRef(new byte[16], 8, 8));
    field.setLocationValue(-30, -40);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(field);
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        // expected: the document is indexed at (-30, -40); actual: count is 0 (the slice is never updated)
        assertEquals(1, new IndexSearcher(r).count(LatLonPoint.newBoxQuery("p", -31, -29, -41, -39)));
      }
    }
  }
}
