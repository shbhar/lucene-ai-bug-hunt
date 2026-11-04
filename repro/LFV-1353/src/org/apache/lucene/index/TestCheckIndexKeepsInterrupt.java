package org.apache.lucene.index;

import java.io.OutputStream;
import java.io.PrintStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCheckIndexKeepsInterrupt extends LuceneTestCase {

  public void testInterruptAtEndOfCheckIsKept() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(new Document());
      }
      PrintStream interruptAtEnd =
          new PrintStream(OutputStream.nullOutputStream()) {
            @Override
            public void println(String line) {
              if (line.startsWith("Took ")) {
                Thread.currentThread().interrupt();
              }
            }
          };
      try (CheckIndex checker = new CheckIndex(dir)) {
        checker.setThreadCount(2);
        checker.setInfoStream(interruptAtEnd);
        checker.checkIndex();
      }
      // expected: the interrupt raised during checkIndex() is still set; actual: it was cleared
      assertTrue(Thread.interrupted());
    }
  }
}
