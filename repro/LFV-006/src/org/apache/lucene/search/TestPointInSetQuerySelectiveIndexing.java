package org.apache.lucene.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.NumericUtils;

public class TestPointInSetQuerySelectiveIndexing extends LuceneTestCase {

  public void testSetQueryOnOneIndexDimensionOfTwo() throws Exception {
    FieldType type = new FieldType();
    type.setDimensions(2, 1, Integer.BYTES);
    type.freeze();
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int[] point : new int[][] {{30, 5}, {10, 9}, {20, 9}}) {
          byte[] packed = new byte[2 * Integer.BYTES];
          NumericUtils.intToSortableBytes(point[0], packed, 0);
          NumericUtils.intToSortableBytes(point[1], packed, Integer.BYTES);
          Document doc = new Document();
          doc.add(new Field("f", packed, type));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        // expected 2 hits (indexed values 30 and 10), actual 1: the doc with value 10 is missed
        assertEquals(2, new IndexSearcher(reader).count(IntPoint.newSetQuery("f", 10, 30)));
      }
    }
  }
}
