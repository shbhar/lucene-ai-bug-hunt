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
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.store.BaseDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Version;

/**
 * An index created by the next Lucene major (commit written by that major, created major =
 * LATEST.major + 1, segments_N format unchanged) is newer than this Lucene version, so opening it
 * should throw {@link IndexFormatTooNewException}. It throws {@link IllegalArgumentException}
 * ("indexCreatedVersionMajor is in the future") from the {@link SegmentInfos} constructor instead.
 */
public class TestOpenIndexFromNewerMajor extends LuceneTestCase {

  /**
   * Writes a one-document index, records its segments as written by the next major (public
   * SegmentInfo / SegmentInfoFormat API), then sets the two version fields a next-major commit
   * carries, which no public writer of this version can produce: the commit's Lucene version and the
   * index created major (vInts right after the segments_N index header), and recomputes the CRC32
   * footer.
   */
  private static void writeIndexFromNextMajor(Directory dir) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new StringField("id", "1", Field.Store.YES));
      w.addDocument(doc);
      w.commit();
    }
    int nextMajor = Version.LATEST.major + 1;
    Version nextVersion = Version.fromBits(nextMajor, 0, 0);
    // segments written by the next major (its codec name may well be unchanged)
    SegmentInfos old = SegmentInfos.readLatestCommit(dir);
    SegmentInfos sis = new SegmentInfos(Version.LATEST.major);
    for (SegmentCommitInfo sci : old) {
      SegmentInfo si = sci.info;
      SegmentInfo rewritten =
          new SegmentInfo(
              dir,
              nextVersion,
              nextVersion,
              si.name,
              si.maxDoc(),
              si.getUseCompoundFile(),
              si.getHasBlocks(),
              si.getCodec(),
              si.getDiagnostics(),
              si.getId(),
              si.getAttributes(),
              si.getIndexSort());
      String siFile = IndexFileNames.segmentFileName(si.name, "", "si");
      Set<String> files = new HashSet<>(si.files());
      files.remove(siFile);
      rewritten.setFiles(files);
      dir.deleteFile(siFile);
      si.getCodec().segmentInfoFormat().write(dir, rewritten, IOContext.DEFAULT);
      dir.sync(Set.of(siFile));
      sis.add(
          new SegmentCommitInfo(
              rewritten,
              sci.getDelCount(),
              sci.getSoftDelCount(),
              sci.getDelGen(),
              sci.getFieldInfosGen(),
              sci.getDocValuesGen(),
              sci.getId()));
    }
    sis.updateGenerationVersionAndCounter(old);
    sis.commit(dir);
    dir.deleteFile(old.getSegmentsFileName());
    // the commit itself: written by the next major, for an index created by the next major
    String name = sis.getSegmentsFileName();
    byte[] bytes;
    try (IndexInput in = dir.openInput(name, IOContext.READONCE)) {
      bytes = new byte[(int) in.length()];
      in.readBytes(bytes, 0, bytes.length);
    }
    int off =
        CodecUtil.indexHeaderLength(
            "segments", Long.toString(sis.getGeneration(), Character.MAX_RADIX));
    assertTrue(Version.LATEST.major < 127 && Version.LATEST.minor < 128);
    assertEquals(Version.LATEST.major, bytes[off]); // commit Lucene version: major
    assertEquals(Version.LATEST.minor, bytes[off + 1]); // minor
    assertEquals(Version.LATEST.bugfix, bytes[off + 2]); // bugfix
    assertEquals(Version.LATEST.major, bytes[off + 3]); // index created major
    bytes[off] = (byte) nextMajor;
    bytes[off + 1] = 0;
    bytes[off + 2] = 0;
    bytes[off + 3] = (byte) nextMajor;
    CRC32 crc = new CRC32();
    crc.update(bytes, 0, bytes.length - Long.BYTES);
    long checksum = crc.getValue();
    for (int i = 0; i < Long.BYTES; i++) {
      bytes[bytes.length - 1 - i] = (byte) (checksum >>> (8 * i));
    }
    dir.deleteFile(name);
    try (IndexOutput out = dir.createOutput(name, IOContext.DEFAULT)) {
      out.writeBytes(bytes, 0, bytes.length);
    }
  }

  public void testDirectoryReaderOpen() throws IOException {
    try (BaseDirectoryWrapper dir = newFSDirectory(createTempDir())) {
      dir.setCheckIndexOnClose(false); // this Lucene version cannot read the index
      writeIndexFromNextMajor(dir);
      expectThrows(IndexFormatTooNewException.class, () -> DirectoryReader.open(dir));
    }
  }

  public void testIndexWriterOpen() throws IOException {
    try (BaseDirectoryWrapper dir = newFSDirectory(createTempDir())) {
      dir.setCheckIndexOnClose(false);
      writeIndexFromNextMajor(dir);
      expectThrows(
          IndexFormatTooNewException.class, () -> new IndexWriter(dir, new IndexWriterConfig()));
    }
  }
}
