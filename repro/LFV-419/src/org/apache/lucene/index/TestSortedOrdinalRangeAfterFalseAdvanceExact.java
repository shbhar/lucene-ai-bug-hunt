package org.apache.lucene.index;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FixedBitSet;

public class TestSortedOrdinalRangeAfterFalseAdvanceExact extends LuceneTestCase {

  public void testDocWithoutValueIsNotMarked() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        for (String value : new String[] {"a", null, "b"}) {
          Document doc = new Document();
          if (value != null) {
            doc.add(new SortedDocValuesField("f", new BytesRef(value)));
          }
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        SortedDocValues dv = getOnlyLeafReader(reader).getSortedDocValues("f");
        int ordA = dv.lookupTerm(new BytesRef("a"));
        assertFalse(dv.advanceExact(1));
        FixedBitSet bits = new FixedBitSet(3);
        dv.ordinalRangeIntoBitSet(1, 3, ordA, ordA, bits, 0);
        // expected: doc 1 (no value) is not marked; actual: marked with doc 0's ordinal
        assertFalse("doc 1 has no value but was marked", bits.get(1));
      }
    }
  }
}
