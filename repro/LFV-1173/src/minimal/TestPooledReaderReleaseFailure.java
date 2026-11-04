package minimal;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterMergePolicy;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOSupplier;

public class TestPooledReaderReleaseFailure extends LuceneTestCase {

  public void testNrtOpenAfterThrowingClosedListener() throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setMergePolicy(
        new FilterMergePolicy(new TieredMergePolicy()) {
          @Override
          public int numDeletesToMerge(
              SegmentCommitInfo info, int delCount, IOSupplier<CodecReader> readerSupplier)
              throws IOException {
            readerSupplier.get();
            return super.numDeletesToMerge(info, delCount, readerSupplier);
          }
        });
    try (IndexWriter w = new IndexWriter(new ByteBuffersDirectory(), iwc)) {
      for (String id : new String[] {"a", "b"}) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.NO));
        w.addDocument(doc);
      }
      DirectoryReader r = DirectoryReader.open(w);
      r.leaves().get(0).reader().getReaderCacheHelper()
          .addClosedListener(key -> { throw new IOException("listener"); });
      r.close();
      w.deleteDocuments(new Term("id", "a"));
      expectThrows(IOException.class, () -> DirectoryReader.open(w));
      // Expected: the next NRT open works (numDocs 1); actual: AlreadyClosedException, the pool kept the closed reader.
      try (DirectoryReader r2 = DirectoryReader.open(w)) {
        assertEquals(1, r2.numDocs());
      }
    }
  }
}
