package org.apache.lucene.index;

import java.io.IOException;
import java.util.Random;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.LuceneTestCase.SuppressFileSystems;
import org.apache.lucene.tests.util.TestUtil;

@SuppressFileSystems("WindowsFS")
public class TestMergeInstanceReopenByName extends LuceneTestCase {

  public void testAddIndexesCopiesTheReadersOwnStoredFields() throws IOException {
    try (MMapDirectory dir = new MMapDirectory(createTempDir());
        Directory target = new ByteBuffersDirectory()) {
      dir.setReadAdvice(MMapDirectory.ADVISE_BY_CONTEXT);
      IndexWriter w1 = new IndexWriter(dir, config());
      w1.addDocument(doc("old"));
      DirectoryReader reader = DirectoryReader.open(w1);
      w1.rollback();
      new IndexWriter(dir, config()).rollback();
      try (IndexWriter w3 = new IndexWriter(dir, config())) {
        w3.addDocument(doc("new"));
      }
      try (IndexWriter t = new IndexWriter(target, config())) {
        t.addIndexes((CodecReader) getOnlyLeafReader(reader));
      }
      reader.close();
      try (DirectoryReader copy = DirectoryReader.open(target)) {
        // expected: the reader's own "old"; actual: "new" from the later segment that reused the name _0
        assertEquals("old", copy.storedFields().document(0).get("id"));
      }
    }
  }

  private static IndexWriterConfig config() {
    return new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
  }

  private static Document doc(String id) {
    byte[] pad = new byte[4096];
    new Random(0).nextBytes(pad);
    Document doc = new Document();
    doc.add(new StoredField("id", id));
    doc.add(new StoredField("pad", pad));
    return doc;
  }
}
