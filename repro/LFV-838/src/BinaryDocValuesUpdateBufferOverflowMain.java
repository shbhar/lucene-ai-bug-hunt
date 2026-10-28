/*
 * Licensed to the Apache Software Foundation (ASF) under the Apache License, Version 2.0.
 *
 * Plain main() form of TestBinaryDocValuesUpdateBufferOverflow for releases whose test framework
 * lives in another package: exits 1 when the commit of one accepted binary doc-values update
 * (1 MiB value, term matching 2,100 documents of one segment) fails or closes the writer.
 * Run with -Xmx8g. Public API only (available since 8.0).
 */
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.BytesRef;

public class BinaryDocValuesUpdateBufferOverflowMain {
  public static void main(String[] args) throws Exception {
    int numDocs = Integer.getInteger("h321.numDocs", 2100);
    ByteBuffersDirectory dir = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(null).setMergePolicy(NoMergePolicy.INSTANCE));
    for (int i = 0; i < numDocs; i++) {
      Document doc = new Document();
      doc.add(new StringField("group", "g", Field.Store.NO));
      doc.add(new BinaryDocValuesField("payload", new BytesRef(new byte[] {(byte) i})));
      w.addDocument(doc);
    }
    w.commit();
    byte[] big = new byte[1 << 20];
    for (int i = 0; i < big.length; i++) big[i] = (byte) (i * 31 + 7);
    w.updateBinaryDocValue(new Term("group", "g"), "payload", new BytesRef(big));
    Throwable failure = null;
    try {
      w.commit();
    } catch (Throwable t) {
      failure = t;
    }
    System.out.println("numDocs=" + numDocs + " commit failure: " + failure + "; writer open: " + w.isOpen()
        + "; tragic exception: " + w.getTragicException());
    if (failure != null || w.isOpen() == false) {
      System.exit(1);
    }
    w.close();
    System.out.println("OK: update committed, writer open");
  }
}
