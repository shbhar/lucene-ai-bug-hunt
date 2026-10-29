package org.apache.lucene.index;

import java.util.List;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestFrozenTypeCacheMultiTypeField extends LuceneTestCase {

  public void testDocumentWithoutDocValuesRejected() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(
          List.of(
              new StringField("id", "a", Field.Store.NO),
              new SortedDocValuesField("id", new BytesRef("a"))));
      // expected IllegalArgumentException (doc lacks the doc values "id" has), actual: accepted
      expectThrows(
          IllegalArgumentException.class,
          () -> w.addDocument(List.of(new StringField("id", "b", Field.Store.NO))));
    }
  }
}
