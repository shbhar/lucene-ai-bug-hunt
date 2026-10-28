/*
 * Licensed to the Apache Software Foundation (ASF) under the Apache License, Version 2.0.
 *
 * Plain main() form of TestFirstDocValuesUpdateBufferedTwice#testFirstUpdateIsAccountedOnce for any
 * release: exits 1 when the first 1 MiB binary doc-values update of a field adds clearly more to
 * IndexWriter#ramBytesUsed() than each later one. Public API only.
 */
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

public class FirstDocValuesUpdateBufferedTwiceMain {
  public static void main(String[] args) throws Exception {
    Path tmp = Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")), "first-dv-update");
    try (Directory dir = FSDirectory.open(tmp);
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(null).setMergePolicy(NoMergePolicy.INSTANCE))) {
      Document doc = new Document();
      doc.add(new StringField("id", "x", Field.Store.NO));
      doc.add(new BinaryDocValuesField("payload", new BytesRef("v")));
      w.addDocument(doc);
      w.commit();
      int len = 1 << 20;
      long r0 = w.ramBytesUsed();
      w.updateBinaryDocValue(new Term("id", "none-1"), "payload", new BytesRef(new byte[len]));
      long r1 = w.ramBytesUsed();
      w.updateBinaryDocValue(new Term("id", "none-2"), "payload", new BytesRef(new byte[len]));
      long r2 = w.ramBytesUsed();
      long first = r1 - r0, second = r2 - r1;
      System.out.println("RAM accounted per 1 MiB binary update: first=" + first + " second=" + second);
      if (first >= second + len / 4) {
        System.out.println("FAIL: the first update of the field is accounted about twice");
        System.exit(1);
      }
      System.out.println("OK");
    }
  }
}
