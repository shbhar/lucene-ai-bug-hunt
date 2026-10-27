package org.apache.lucene.index;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Probe: does a single (non-block) document that IndexWriter rejects leave a segment without the
 * parent field? Prints PROBE lines; each test asserts that forceMerge(1) works afterwards.
 */
public class TestSingleRejectedDocProbe extends LuceneTestCase {

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  private void run(String label, Document bad) throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig(new MockAnalyzer(random()))
              .setParentField("_parent")
              .setSoftDeletesField("_soft")
              .setMergePolicy(
                  new SoftDeletesRetentionMergePolicy(
                      "_soft", MatchAllDocsQuery::new, new LogDocMergePolicy()))
              .setMergeScheduler(new SerialMergeScheduler());
      IndexWriter w = new IndexWriter(dir, iwc);
      w.addDocument(doc("a"));
      w.commit();
      Throwable t = expectThrows(Throwable.class, () -> w.addDocument(bad));
      System.out.println("PROBE " + label + " rejection: " + t + " open=" + w.isOpen());
      w.commit();
      w.addDocument(doc("b"));
      w.commit();
      StringBuilder sb = new StringBuilder();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        for (LeafReaderContext ctx : r.leaves()) {
          sb.append("[maxDoc=").append(ctx.reader().maxDoc()).append(" numDocs=")
              .append(ctx.reader().numDocs()).append(" parent=")
              .append(ctx.reader().getFieldInfos().getParentField()).append(" fields=")
              .append(ctx.reader().getFieldInfos().size()).append("]");
        }
      }
      System.out.println("PROBE " + label + " committed leaves=" + sb);
      try {
        w.forceMerge(1);
        System.out.println("PROBE " + label + " forceMerge(1) OK");
      } catch (Throwable e) {
        System.out.println("PROBE " + label + " forceMerge(1) FAIL " + e + " open=" + w.isOpen());
        throw new AssertionError(label + ": forceMerge(1) failed", e);
      }
      w.close();
    }
  }

  /** Reserved parent field name in a single document: rejected in the first pass. */
  public void testReservedFieldName() throws IOException {
    Document bad = doc("bad");
    bad.add(new NumericDocValuesField("_parent", 1));
    run("reserved-name", bad);
  }

  /** A single document with an immense term (rejected in the second pass): control. */
  public void testImmenseTermSingleDoc() throws IOException {
    Document bad = doc("bad");
    bad.add(new StringField("body", "x".repeat(IndexWriter.MAX_TERM_LENGTH + 1), Field.Store.NO));
    run("immense-single", bad);
  }
}
