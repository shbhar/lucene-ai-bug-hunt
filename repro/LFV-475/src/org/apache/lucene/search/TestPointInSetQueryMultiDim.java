package org.apache.lucene.search;

import java.util.Iterator;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestPointInSetQueryMultiDim extends LuceneTestCase {

  public void testSetContainingIndexedPoint() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new IntPoint("p", 1, 0));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        Iterator<BytesRef> sorted = List.of(IntPoint.pack(0, 1), IntPoint.pack(1, 0)).iterator();
        Query q =
            new PointInSetQuery(
                "p",
                2,
                Integer.BYTES,
                new PointInSetQuery.Stream() {
                  @Override
                  public BytesRef next() {
                    return sorted.hasNext() ? sorted.next() : null;
                  }
                }) {
              @Override
              protected String toString(byte[] value) {
                return new BytesRef(value).toString();
              }
            };
        // the set {(0,1), (1,0)} contains the only indexed point (1,0): expected 1 hit, actual 0
        assertEquals("hits for the set {(0,1), (1,0)}", 1, new IndexSearcher(r).count(q));
      }
    }
  }
}
