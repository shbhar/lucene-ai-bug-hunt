package org.apache.lucene.index.memory;

import java.nio.charset.StandardCharsets;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestMemoryIndexSortedDocValueCopy extends LuceneTestCase {

  public void testValueNotAffectedByLaterBufferReuse() throws Exception {
    byte[] buffer = "red".getBytes(StandardCharsets.UTF_8);
    MemoryIndex mi = new MemoryIndex();
    mi.addField(new SortedDocValuesField("color", new BytesRef(buffer)), null);
    System.arraycopy("tan".getBytes(StandardCharsets.UTF_8), 0, buffer, 0, 3);

    SortedDocValues dv =
        mi.createSearcher().getIndexReader().leaves().get(0).reader().getSortedDocValues("color");
    assertTrue(dv.advanceExact(0));
    // expected "red" (the value added, as up to 9.0.0 and in IndexWriter); actual "tan" (the caller's later bytes)
    assertEquals("red", dv.lookupOrd(dv.ordValue()).utf8ToString());
  }
}
