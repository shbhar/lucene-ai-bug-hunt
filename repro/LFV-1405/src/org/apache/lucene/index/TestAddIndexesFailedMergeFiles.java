package org.apache.lucene.index;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestAddIndexesFailedMergeFiles extends LuceneTestCase {

  public void testFailedMergeLeavesNoUnreferencedFiles() throws IOException {
    // Expected: a failed (non-tragic) addIndexes leaves no unreferenced files after commit(); actual: its partial segment files remain.
    Directory src = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      Document doc = new Document();
      doc.add(new StringField("id", "0", Field.Store.YES));
      w.addDocument(doc);
    }
    Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexOutput createOutput(String name, IOContext context) throws IOException {
            if (name.endsWith(".fnm")) {
              throw new IOException("simulated disk full");
            }
            return super.createOutput(name, context);
          }
        };
    try (DirectoryReader reader = DirectoryReader.open(src);
        IndexWriter w =
            new IndexWriter(
                dir,
                new IndexWriterConfig()
                    .setCodec(TestUtil.getDefaultCodec())
                    .setMergeScheduler(new SerialMergeScheduler()))) {
      expectThrows(IOException.class, () -> w.addIndexes((CodecReader) reader.leaves().get(0).reader()));
      assertNull(w.getTragicException());
      w.commit();
      Set<String> unreferenced = new TreeSet<>(Arrays.asList(dir.listAll()));
      unreferenced.removeAll(SegmentInfos.readLatestCommit(dir).files(true));
      unreferenced.remove(IndexWriter.WRITE_LOCK_NAME);
      assertEquals(Set.of(), unreferenced);
    }
  }
}
