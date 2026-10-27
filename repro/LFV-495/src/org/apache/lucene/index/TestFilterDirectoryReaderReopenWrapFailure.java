/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.index;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.Lock;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * DirectoryReader.openIfChanged on a FilterDirectoryReader opens a new inner reader and then wraps
 * it. If wrapping throws, the new inner reader is neither returned nor closed: the references it
 * took on the segment readers it shares with the old reader are never released.
 */
public class TestFilterDirectoryReaderReopenWrapFailure extends LuceneTestCase {

  private static Document doc(String id, boolean softDeleted) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    if (softDeleted) {
      d.add(new NumericDocValuesField("soft", 1));
    }
    return d;
  }

  /** Stock SoftDeletesDirectoryReaderWrapper; an I/O error while it reads the new segment. */
  public void testSoftDeletesWrapperReadErrorDuringReopen() throws Exception {
    ReadErrorDirectory dir = new ReadErrorDirectory(new ByteBuffersDirectory());
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec()) // the production codec: plain .dvd data files
            .setSoftDeletesField("soft")
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setUseCompoundFile(false);
    IndexWriter w = new IndexWriter(dir, iwc);
    w.addDocument(doc("0", false));
    w.commit();
    DirectoryReader reader =
        new SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), "soft");
    SegmentReader oldSegment =
        (SegmentReader) FilterDirectoryReader.unwrap(reader).leaves().get(0).reader();
    assertEquals(1, oldSegment.getRefCount());

    // a second segment with a soft-deleted document: the wrapper must read its doc values
    w.addDocument(doc("1", false));
    w.addDocument(doc("2", true));
    w.commit();
    w.close();

    dir.failDataReadsOfSegment = "_1"; // reads of _1's doc values fail from now on
    UncheckedIOException e =
        expectThrows(UncheckedIOException.class, () -> DirectoryReader.openIfChanged(reader));
    assertTrue(e.getCause().getMessage(), e.getCause().getMessage().contains("simulated"));
    dir.failDataReadsOfSegment = null;

    // openIfChanged returned no reader, so the caller owns only 'reader' (one reference on _0)
    assertEquals(
        "the failed reopen must not keep a reference on the old segment reader",
        1,
        oldSegment.getRefCount());
    reader.close();
    assertEquals("closing the only reader must close the segment", 0, oldSegment.getRefCount());
    dir.close();
  }

  /** Minimal wrapper whose SubReaderWrapper throws for the new segment. */
  public void testWrapperThrowsDuringReopen() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE));
    w.addDocument(doc("0", false));
    w.commit();
    boolean[] fail = {false};
    DirectoryReader reader = new ThrowingWrapper(DirectoryReader.open(dir), fail);
    SegmentReader oldSegment =
        (SegmentReader) FilterDirectoryReader.unwrap(reader).leaves().get(0).reader();
    w.addDocument(doc("1", false));
    w.commit();
    w.close();

    fail[0] = true;
    expectThrows(IllegalStateException.class, () -> DirectoryReader.openIfChanged(reader));
    assertEquals(
        "the failed reopen must not keep a reference on the old segment reader",
        1,
        oldSegment.getRefCount());
    reader.close();
    assertEquals("closing the only reader must close the segment", 0, oldSegment.getRefCount());
    dir.close();
  }

  private static final class ThrowingWrapper extends FilterDirectoryReader {
    private final boolean[] fail;

    ThrowingWrapper(DirectoryReader in, boolean[] fail) throws IOException {
      super(
          in,
          new SubReaderWrapper() {
            @Override
            public LeafReader wrap(LeafReader reader) {
              if (fail[0]) {
                throw new IllegalStateException("wrapper rejects this reader");
              }
              return reader;
            }
          });
      this.fail = fail;
    }

    @Override
    protected DirectoryReader doWrapDirectoryReader(DirectoryReader in) throws IOException {
      return new ThrowingWrapper(in, fail);
    }

    @Override
    public CacheHelper getReaderCacheHelper() {
      return in.getReaderCacheHelper();
    }
  }

  /**
   * Fails every read through a slice or clone of the doc-values files of one segment (the file
   * header and footer, which the codec checks when it opens a file, are read through the original
   * input and still work), like a disk read error.
   */
  private static final class ReadErrorDirectory extends Directory {
    private final Directory in;
    volatile String failDataReadsOfSegment;

    ReadErrorDirectory(Directory in) {
      this.in = in;
    }

    @Override
    public IndexInput openInput(String name, IOContext context) throws IOException {
      IndexInput input = in.openInput(name, context);
      String seg = failDataReadsOfSegment;
      if (seg != null && name.startsWith(seg + "_") && name.contains(".dv")) {
        return new FailingSlices(input, this, seg);
      }
      return input;
    }

    @Override
    public String[] listAll() throws IOException {
      return in.listAll();
    }

    @Override
    public void deleteFile(String name) throws IOException {
      in.deleteFile(name);
    }

    @Override
    public long fileLength(String name) throws IOException {
      return in.fileLength(name);
    }

    @Override
    public IndexOutput createOutput(String name, IOContext context) throws IOException {
      return in.createOutput(name, context);
    }

    @Override
    public IndexOutput createTempOutput(String prefix, String suffix, IOContext context)
        throws IOException {
      return in.createTempOutput(prefix, suffix, context);
    }

    @Override
    public void sync(Collection<String> names) throws IOException {
      in.sync(names);
    }

    @Override
    public void syncMetaData() throws IOException {
      in.syncMetaData();
    }

    @Override
    public void rename(String source, String dest) throws IOException {
      in.rename(source, dest);
    }

    @Override
    public Lock obtainLock(String name) throws IOException {
      return in.obtainLock(name);
    }

    @Override
    public void close() throws IOException {
      in.close();
    }

    @Override
    public void copyFrom(Directory from, String src, String dest, IOContext context)
        throws IOException {
      in.copyFrom(from, src, dest, context);
    }

    @Override
    public Set<String> getPendingDeletions() throws IOException {
      return in.getPendingDeletions();
    }
  }

  private static final class FailingSlices extends IndexInput {
    private final IndexInput in;
    private final ReadErrorDirectory dir;
    private final String seg;
    private final boolean fail;

    FailingSlices(IndexInput in, ReadErrorDirectory dir, String seg) {
      this(in, dir, seg, false);
    }

    private FailingSlices(IndexInput in, ReadErrorDirectory dir, String seg, boolean fail) {
      super("FailingSlices(" + in + ")");
      this.in = in;
      this.dir = dir;
      this.seg = seg;
      this.fail = fail;
    }

    private void maybeFail() throws IOException {
      if (fail && seg.equals(dir.failDataReadsOfSegment)) {
        throw new IOException("simulated read error: " + this);
      }
    }

    @Override
    public void close() throws IOException {
      if (fail == false) {
        in.close();
      }
    }

    @Override
    public long getFilePointer() {
      return in.getFilePointer();
    }

    @Override
    public void seek(long pos) throws IOException {
      in.seek(pos);
    }

    @Override
    public long length() {
      return in.length();
    }

    @Override
    public IndexInput slice(String sliceDescription, long offset, long length)
        throws IOException {
      return new FailingSlices(in.slice(sliceDescription, offset, length), dir, seg, true);
    }

    @Override
    public IndexInput clone() {
      return new FailingSlices(in.clone(), dir, seg, true);
    }

    @Override
    public byte readByte() throws IOException {
      maybeFail();
      return in.readByte();
    }

    @Override
    public void readBytes(byte[] b, int offset, int len) throws IOException {
      maybeFail();
      in.readBytes(b, offset, len);
    }
  }
}
