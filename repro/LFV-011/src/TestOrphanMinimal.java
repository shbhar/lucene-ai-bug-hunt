import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/** Minimal deterministic repro: the exception is NOT caught, so JUnit prints the full trace. */
public class TestOrphanMinimal extends LuceneTestCase {

  static Document doc(String id, long v) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new NumericDocValuesField("s", v));
    return d;
  }

  public void testMergeAfterDeletingBlockParent() throws Exception {
    run(TestUtil.getDefaultCodec());
  }

  /** Same, with the test framework's Asserting codec (as random test codecs would pick). */
  public void testMergeAfterDeletingBlockParentAssertingCodec() throws Exception {
    run(new org.apache.lucene.tests.codecs.asserting.AssertingCodec());
  }

  private void run(org.apache.lucene.codecs.Codec codec) throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(
                dir,
                new IndexWriterConfig()
                    .setCodec(codec)
                    .setIndexSort(new Sort(new SortField("s", SortField.Type.LONG)))
                    .setParentField("__parent")
                    .setMergePolicy(new LogDocMergePolicy())
                    .setMergeScheduler(new SerialMergeScheduler()))) {
      w.addDocuments(List.of(doc("c1", 50), doc("p1", 10))); // block: child, parent
      w.commit();
      w.deleteDocuments(new Term("id", "p1")); // delete only the parent
      w.addDocument(doc("x", 5));
      w.commit();
      w.forceMerge(1); // OK -> one segment [x(parent), c1(child with no parent after it)]
      w.addDocument(doc("y", 7));
      w.commit();
      w.forceMerge(1); // throws (IndexOutOfBoundsException with the default codec)
    }
  }
}
