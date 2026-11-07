package org.apache.lucene.index;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestNrtAppliedPurgeOfSoftDeletedDoc extends LuceneTestCase {

  // Expected: hasUncommittedChanges() is true, since the next commit() writes the hard delete; actual: false.
  public void testHardDeleteOfSoftDeletedDocAppliedByNrtOpen() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig().setSoftDeletesField("soft").setMergePolicy(NoMergePolicy.INSTANCE);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (String id : new String[] {"a", "b"}) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.NO));
        w.addDocument(doc);
      }
      w.updateDocValues(new Term("id", "a"), new NumericDocValuesField("soft", 1));
      w.commit();

      w.deleteDocuments(new Term("id", "a"));
      DirectoryReader.open(w).close();
      boolean uncommitted = w.hasUncommittedChanges();

      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.numDocs());
      }
      assertTrue(uncommitted);
    }
  }
}
