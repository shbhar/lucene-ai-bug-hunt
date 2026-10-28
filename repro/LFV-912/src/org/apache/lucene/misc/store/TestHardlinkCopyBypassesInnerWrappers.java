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
package org.apache.lucene.misc.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.Lock;
import org.apache.lucene.store.LockValidatingDirectoryWrapper;
import org.apache.lucene.store.TrackingDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * HardlinkCopyDirectoryWrapper#copyFrom unwraps both directory chains down to the innermost
 * FSDirectory and hard-links the file there. Every FilterDirectory between the wrapper and the
 * FSDirectory (and every FilterDirectory around the source FSDirectory) is skipped, although
 * Directory#copyFrom requires that "any per-file bookkeeping performed by createOutput must also
 * cover copied files" and the copy must read back as the source file.
 *
 * <p>Only public API is used. Each test first checks the same chain without the hard-link wrapper
 * (or the hard-link wrapper directly over the FSDirectory) as a control.
 */
public class TestHardlinkCopyBypassesInnerWrappers extends LuceneTestCase {

  /** Skips the test when the file system of the temp dir cannot hard-link. */
  private static void assumeHardLinks(Path base) throws IOException {
    Path a = Files.createFile(base.resolve("probe-a"));
    Path b = base.resolve("probe-b");
    try {
      Files.createLink(b, a);
    } catch (UnsupportedOperationException | IOException e) {
      assumeTrue("hard links are not supported: " + e, false);
    }
    Object ka = Files.readAttributes(a, "basic:fileKey").get("fileKey");
    Object kb = Files.readAttributes(b, "basic:fileKey").get("fileKey");
    assumeTrue("hard links are not supported", ka != null && ka.equals(kb));
    Files.delete(b);
    Files.delete(a);
  }

  private static void writeFile(Directory dir, String name, byte[] bytes) throws IOException {
    try (IndexOutput out = dir.createOutput(name, IOContext.DEFAULT)) {
      out.writeBytes(bytes, bytes.length);
    }
  }

  private static byte[] readFile(Directory dir, String name) throws IOException {
    try (IndexInput in = dir.openInput(name, IOContext.READONCE)) {
      byte[] b = new byte[(int) in.length()];
      in.readBytes(b, 0, b.length);
      return b;
    }
  }

  private static byte[] someBytes() {
    byte[] b = new byte[64];
    for (int i = 0; i < b.length; i++) {
      b[i] = (byte) i;
    }
    return b;
  }

  /**
   * In-tree wrapper below the hard-link wrapper: TrackingDirectoryWrapper records the names its
   * createOutput creates (and its own copyFrom records dest), but a copy through
   * HardlinkCopyDirectoryWrapper(TrackingDirectoryWrapper(FSDirectory)) is not recorded.
   */
  public void testCopyIsRecordedByInnerTrackingDirectoryWrapper() throws IOException {
    Path base = createTempDir();
    assumeHardLinks(base);
    try (Directory src = FSDirectory.open(base.resolve("src"));
        TrackingDirectoryWrapper controlTracking =
            new TrackingDirectoryWrapper(FSDirectory.open(base.resolve("control")));
        TrackingDirectoryWrapper tracking =
            new TrackingDirectoryWrapper(FSDirectory.open(base.resolve("dst")));
        Directory dst = new HardlinkCopyDirectoryWrapper(tracking)) {
      writeFile(src, "a.bin", someBytes());

      // control: the same wrapper chain with a plain FilterDirectory on top
      Directory control = new FilterDirectory(controlTracking) {};
      control.copyFrom(src, "a.bin", "b.bin", IOContext.DEFAULT);
      assertTrue(controlTracking.getCreatedFiles().contains("b.bin"));

      dst.copyFrom(src, "a.bin", "b.bin", IOContext.DEFAULT);
      assertArrayEquals(someBytes(), readFile(dst, "b.bin"));
      assertTrue(
          "copyFrom through HardlinkCopyDirectoryWrapper must be covered by the createOutput"
              + " bookkeeping of the wrapped TrackingDirectoryWrapper; created="
              + tracking.getCreatedFiles(),
          tracking.getCreatedFiles().contains("b.bin"));
    }
  }

  /**
   * In-tree wrapper below the hard-link wrapper: LockValidatingDirectoryWrapper checks its write
   * lock in createOutput and copyFrom. After the lock was released, a copy through
   * HardlinkCopyDirectoryWrapper(LockValidatingDirectoryWrapper(FSDirectory, lock)) must fail like
   * the same copy without the hard-link wrapper does, but it silently creates the file.
   */
  public void testCopyIsValidatedByInnerLockValidatingDirectoryWrapper() throws IOException {
    Path base = createTempDir();
    assumeHardLinks(base);
    try (Directory src = FSDirectory.open(base.resolve("src"));
        FSDirectory controlFs = FSDirectory.open(base.resolve("control"));
        FSDirectory dstFs = FSDirectory.open(base.resolve("dst"))) {
      writeFile(src, "a.bin", someBytes());

      Lock controlLock = controlFs.obtainLock("write.lock");
      controlLock.close(); // the lock is lost
      Directory control = new LockValidatingDirectoryWrapper(controlFs, controlLock);
      expectThrows(
          AlreadyClosedException.class,
          () -> control.copyFrom(src, "a.bin", "b.bin", IOContext.DEFAULT));

      Lock lock = dstFs.obtainLock("write.lock");
      lock.close(); // the lock is lost
      Directory dst = new HardlinkCopyDirectoryWrapper(new LockValidatingDirectoryWrapper(dstFs, lock));
      Throwable t = null;
      try {
        dst.copyFrom(src, "a.bin", "b.bin", IOContext.DEFAULT);
      } catch (AlreadyClosedException e) {
        t = e;
      }
      assertNotNull(
          "copyFrom through HardlinkCopyDirectoryWrapper must be validated by the wrapped"
              + " LockValidatingDirectoryWrapper; created b.bin="
              + Files.exists(base.resolve("dst").resolve("b.bin")),
          t);
    }
  }

  /**
   * A FilterDirectory whose file namespace is the set of files created through it (its
   * createOutput/rename/deleteFile keep the set; listAll/openInput/fileLength only serve it). This
   * is conforming per-file bookkeeping in createOutput, so copied files must be covered.
   */
  static final class OwnFilesDirectory extends FilterDirectory {
    final Set<String> own = new HashSet<>();

    OwnFilesDirectory(Directory in) {
      super(in);
    }

    @Override
    public synchronized IndexOutput createOutput(String name, IOContext context)
        throws IOException {
      IndexOutput out = in.createOutput(name, context);
      own.add(name);
      return out;
    }

    @Override
    public synchronized IndexOutput createTempOutput(String prefix, String suffix, IOContext ctx)
        throws IOException {
      IndexOutput out = in.createTempOutput(prefix, suffix, ctx);
      own.add(out.getName());
      return out;
    }

    @Override
    public synchronized void rename(String source, String dest) throws IOException {
      if (own.contains(source) == false) {
        throw new NoSuchFileException(source);
      }
      in.rename(source, dest);
      own.remove(source);
      own.add(dest);
    }

    @Override
    public synchronized void deleteFile(String name) throws IOException {
      if (own.remove(name) == false) {
        throw new NoSuchFileException(name);
      }
      in.deleteFile(name);
    }

    @Override
    public synchronized String[] listAll() throws IOException {
      return own.stream().sorted().toArray(String[]::new);
    }

    @Override
    public synchronized long fileLength(String name) throws IOException {
      if (own.contains(name) == false) {
        throw new NoSuchFileException(name);
      }
      return in.fileLength(name);
    }

    @Override
    public synchronized IndexInput openInput(String name, IOContext context) throws IOException {
      if (own.contains(name) == false) {
        throw new NoSuchFileException(name);
      }
      return in.openInput(name, context);
    }

    @Override
    public void sync(Collection<String> names) throws IOException {
      in.sync(names);
    }
  }

  private static void writeSmallIndex(Directory dir) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 10; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(i), Field.Store.YES));
        w.addDocument(doc);
      }
      w.commit();
    }
  }

  private static String addIndexesThenSearch(Directory dst, Directory src) throws IOException {
    try (IndexWriter w = new IndexWriter(dst, new IndexWriterConfig())) {
      w.addIndexes(src);
      w.commit();
    }
    try (DirectoryReader r = DirectoryReader.open(dst)) {
      int hits = new IndexSearcher(r).count(new TermQuery(new Term("id", "3")));
      return "numDocs=" + r.numDocs() + " hits=" + hits;
    }
  }

  /**
   * End to end: IndexWriter(HardlinkCopyDirectoryWrapper(OwnFilesDirectory(FSDirectory)))
   * .addIndexes(Directory) commits an index whose segment files the target directory does not
   * know, so the committed index cannot be opened.
   */
  public void testAddIndexesThroughInnerBookkeepingWrapper() throws IOException {
    Path base = createTempDir();
    assumeHardLinks(base);
    try (Directory src = FSDirectory.open(base.resolve("src"))) {
      writeSmallIndex(src);
      // control: same chain without the hard-link wrapper
      try (Directory control = new OwnFilesDirectory(FSDirectory.open(base.resolve("control")))) {
        assertEquals("numDocs=10 hits=1", addIndexesThenSearch(control, src));
      }
      try (Directory dst =
          new HardlinkCopyDirectoryWrapper(
              new OwnFilesDirectory(FSDirectory.open(base.resolve("dst"))))) {
        assertEquals("numDocs=10 hits=1", addIndexesThenSearch(dst, src));
      }
    }
  }

  /**
   * A conforming FilterDirectory that transforms the bytes it writes and reads (here: XOR with a
   * constant; stands for an encrypting or header-adding directory). Checksums are over the logical
   * bytes, so codec headers and footers work through it.
   */
  static final class XorDirectory extends FilterDirectory {
    XorDirectory(Directory in) {
      super(in);
    }

    @Override
    public IndexOutput createOutput(String name, IOContext context) throws IOException {
      return new XorOutput(in.createOutput(name, context));
    }

    @Override
    public IndexOutput createTempOutput(String prefix, String suffix, IOContext context)
        throws IOException {
      return new XorOutput(in.createTempOutput(prefix, suffix, context));
    }

    @Override
    public IndexInput openInput(String name, IOContext context) throws IOException {
      return new XorInput(in.openInput(name, context));
    }
  }

  static final class XorOutput extends IndexOutput {
    private final IndexOutput out;
    private final CRC32 crc = new CRC32();

    XorOutput(IndexOutput out) {
      super("xor(" + out + ")", out.getName());
      this.out = out;
    }

    @Override
    public void close() throws IOException {
      out.close();
    }

    @Override
    public long getFilePointer() {
      return out.getFilePointer();
    }

    @Override
    public long getChecksum() {
      return crc.getValue();
    }

    @Override
    public void writeByte(byte b) throws IOException {
      crc.update(b);
      out.writeByte((byte) (b ^ 0x5A));
    }

    @Override
    public void writeBytes(byte[] b, int off, int len) throws IOException {
      for (int i = 0; i < len; i++) {
        writeByte(b[off + i]);
      }
    }
  }

  static final class XorInput extends IndexInput {
    private final IndexInput in;

    XorInput(IndexInput in) {
      super("xor(" + in + ")");
      this.in = in;
    }

    @Override
    public void close() throws IOException {
      in.close();
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
    public IndexInput slice(String desc, long offset, long length) throws IOException {
      return new XorInput(in.slice(desc, offset, length));
    }

    @Override
    public byte readByte() throws IOException {
      return (byte) (in.readByte() ^ 0x5A);
    }

    @Override
    public void readBytes(byte[] b, int off, int len) throws IOException {
      in.readBytes(b, off, len);
      for (int i = 0; i < len; i++) {
        b[off + i] ^= 0x5A;
      }
    }

    @Override
    public XorInput clone() {
      return new XorInput(in.clone());
    }
  }

  /**
   * Transform below the hard-link wrapper on the target side only (an asymmetric chain): the copy
   * must read back as the source bytes.
   */
  public void testCopyReadsBackSourceBytesThroughInnerTransformingWrapper() throws IOException {
    Path base = createTempDir();
    assumeHardLinks(base);
    try (Directory src = FSDirectory.open(base.resolve("src"));
        Directory control = new FilterDirectory(new XorDirectory(FSDirectory.open(base.resolve("c")))) {};
        Directory dst =
            new HardlinkCopyDirectoryWrapper(new XorDirectory(FSDirectory.open(base.resolve("d"))))) {
      writeFile(src, "a.bin", someBytes());
      control.copyFrom(src, "a.bin", "b.bin", IOContext.DEFAULT);
      assertArrayEquals(someBytes(), readFile(control, "b.bin"));

      dst.copyFrom(src, "a.bin", "b.bin", IOContext.DEFAULT);
      byte[] got = readFile(dst, "b.bin");
      assertTrue(
          "copy must read back the source bytes; got " + Arrays.toString(Arrays.copyOf(got, 4)),
          Arrays.equals(someBytes(), got));
    }
  }

  /** Same through IndexWriter#addIndexes(Directory): the committed index must open and search. */
  public void testAddIndexesThroughInnerTransformingWrapper() throws IOException {
    Path base = createTempDir();
    assumeHardLinks(base);
    try (Directory src = FSDirectory.open(base.resolve("src"))) {
      writeSmallIndex(src);
      try (Directory control =
          new FilterDirectory(new XorDirectory(FSDirectory.open(base.resolve("control")))) {}) {
        assertEquals("numDocs=10 hits=1", addIndexesThenSearch(control, src));
      }
      try (Directory dst =
          new HardlinkCopyDirectoryWrapper(new XorDirectory(FSDirectory.open(base.resolve("dst"))))) {
        assertEquals("numDocs=10 hits=1", addIndexesThenSearch(dst, src));
      }
    }
  }

  /**
   * Transform on the source side only: HardlinkCopyDirectoryWrapper(FSDirectory).copyFrom(
   * XorDirectory(FSDirectory)) must read back what the source directory returns for the file.
   */
  public void testCopyReadsBackSourceBytesFromTransformingSourceWrapper() throws IOException {
    Path base = createTempDir();
    assumeHardLinks(base);
    try (Directory src = new XorDirectory(FSDirectory.open(base.resolve("src")));
        Directory control = FSDirectory.open(base.resolve("c"));
        Directory dst = new HardlinkCopyDirectoryWrapper(FSDirectory.open(base.resolve("d")))) {
      writeFile(src, "a.bin", someBytes());
      assertArrayEquals(someBytes(), readFile(src, "a.bin"));
      control.copyFrom(src, "a.bin", "b.bin", IOContext.DEFAULT);
      assertArrayEquals(someBytes(), readFile(control, "b.bin"));

      dst.copyFrom(src, "a.bin", "b.bin", IOContext.DEFAULT);
      byte[] got = readFile(dst, "b.bin");
      assertTrue(
          "copy must read back the source bytes; got " + Arrays.toString(Arrays.copyOf(got, 4)),
          Arrays.equals(someBytes(), got));
    }
  }

  /**
   * Not a bug, kept as a guard for the oracle: the same transform on both sides (the main use of
   * hard links between transformed directories) is correct through the hard-link wrapper.
   */
  public void testSymmetricTransformIsCorrect() throws IOException {
    Path base = createTempDir();
    assumeHardLinks(base);
    try (Directory src = new XorDirectory(FSDirectory.open(base.resolve("src")));
        Directory dst =
            new HardlinkCopyDirectoryWrapper(new XorDirectory(FSDirectory.open(base.resolve("d"))))) {
      writeFile(src, "a.bin", someBytes());
      dst.copyFrom(src, "a.bin", "b.bin", IOContext.DEFAULT);
      assertArrayEquals(someBytes(), readFile(dst, "b.bin"));
      Path s = base.resolve("src").resolve("a.bin");
      Path d = base.resolve("d").resolve("b.bin");
      // informational: on the unpatched wrapper this copy is a hard link
      System.out.println("symmetric chain hard-linked=" + Files.isSameFile(s, d));
    }
  }
}
