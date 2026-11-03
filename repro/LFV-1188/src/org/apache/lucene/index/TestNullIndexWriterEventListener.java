package org.apache.lucene.index;

import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestNullIndexWriterEventListener extends LuceneTestCase {

  public void testCommitWithNullEventListener() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
        for (int i = 0; i < 12; i++) {
          w.addDocument(new Document());
          w.commit();
        }
      }
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setIndexWriterEventListener(null))) {
        w.addDocument(new Document());
        // expected: commit() succeeds; actual: NullPointerException from IndexWriter.prepareCommitInternal
        w.commit();
      }
    }
  }
}
