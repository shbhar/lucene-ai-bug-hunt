package org.apache.lucene.search;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.FilterIndexInput;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.RandomAccessInput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestDocValuesReadFailureType extends LuceneTestCase {

  public void testSortReportsDocValuesReadFailureAsIOError() throws IOException {
    try (Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexInput openInput(String name, IOContext context) throws IOException {
            IndexInput input = super.openInput(name, context);
            if (name.endsWith(".dvd") == false) {
              return input;
            }
            return new FilterIndexInput(name, input) {
              @Override
              public RandomAccessInput randomAccessSlice(long offset, long length) {
                return new RandomAccessInput() {
                  @Override
                  public long length() {
                    return length;
                  }

                  @Override
                  public byte readByte(long pos) throws IOException {
                    throw new IOException("simulated read failure");
                  }

                  @Override
                  public short readShort(long pos) throws IOException {
                    throw new IOException("simulated read failure");
                  }

                  @Override
                  public int readInt(long pos) throws IOException {
                    throw new IOException("simulated read failure");
                  }

                  @Override
                  public long readLong(long pos) throws IOException {
                    throw new IOException("simulated read failure");
                  }
                };
              }
            };
          }
        }) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      iwc.setUseCompoundFile(false);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (long v : new long[] {1, 2}) {
          Document doc = new Document();
          doc.add(new NumericDocValuesField("n", v));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Sort sort = new Sort(new SortField("n", SortField.Type.LONG));
        // expected: IOException or UncheckedIOException; actual: plain RuntimeException(IOException)
        expectThrowsAnyOf(
            List.of(IOException.class, UncheckedIOException.class),
            () -> searcher.search(MatchAllDocsQuery.INSTANCE, 2, sort));
      }
    }
  }
}
