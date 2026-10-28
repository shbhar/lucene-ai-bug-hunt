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

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * The class javadoc of {@link SegmentInfos} documents segments_N as
 *
 * <pre>
 * segments_N: Header, LuceneVersion, Version, NameCounter, SegCount, MinSegmentLuceneVersion,
 *   &lt;SegName, SegID, SegCodec, DelGen, DeletionCount, FieldInfosGen, DocValuesGen,
 *   UpdatesFiles, DocValuesOverlays&gt;<sup>SegCount</sup>, CommitUserData, Footer
 * </pre>
 *
 * with LuceneVersion as three vInts, NameCounter, SegCount and DeletionCount as {@code
 * DataOutput#writeInt} Int32 and Version, DelGen, FieldInfosGen and DocValuesGen as {@code
 * DataOutput#writeLong} Int64. These tests parse a segments_N file written by IndexWriter with that
 * grammar. Only public API is used.
 */
public class TestSegmentInfosJavadocGrammar extends LuceneTestCase {

  private static Directory index() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      Document doc = new Document();
      doc.add(new StringField("id", "1", Field.Store.NO));
      w.addDocument(doc);
      w.commit();
    }
    return dir;
  }

  private interface Int64 {
    long read(DataInput in) throws Exception;
  }

  private void parse(boolean bigEndian) throws Exception {
    try (Directory dir = index()) {
      SegmentInfos sis = SegmentInfos.readLatestCommit(dir);
      Int64 int64 = bigEndian ? CodecUtil::readBELong : DataInput::readLong;
      try (IndexInput in = dir.openInput(sis.getSegmentsFileName(), IOContext.READONCE)) {
        CodecUtil.checkIndexHeader(
            in,
            "segments",
            0,
            Integer.MAX_VALUE,
            sis.getId(),
            Long.toString(sis.getGeneration(), Character.MAX_RADIX));
        // LuceneVersion: three vInts
        in.readVInt();
        in.readVInt();
        in.readVInt();
        assertEquals("Version", sis.getVersion(), int64.read(in));
        long nameCounter = bigEndian ? CodecUtil.readBEInt(in) : in.readInt();
        assertEquals("NameCounter", sis.counter, nameCounter);
        int segCount = bigEndian ? CodecUtil.readBEInt(in) : in.readInt();
        assertEquals("SegCount", sis.size(), segCount);
      }
    }
  }

  /** The literal reading: the Int32/Int64 links point at little-endian DataOutput methods. */
  public void testDocumentedSequenceParsesWrittenFile() throws Exception {
    parse(false);
  }

  /** A generous reading that assumes the integers are big-endian. */
  public void testDocumentedSequenceParsesWrittenFileBigEndian() throws Exception {
    parse(true);
  }

  /**
   * Skip the commit-level prefix the way the writer lays it out, then read one segment entry with
   * the documented per-segment sequence (SegName, SegID, SegCodec, DelGen, DeletionCount,
   * FieldInfosGen, DocValuesGen, UpdatesFiles, DocValuesOverlays) followed by CommitUserData and
   * the footer.
   */
  public void testDocumentedSegmentEntryParsesWrittenFile() throws Exception {
    try (Directory dir = index()) {
      SegmentInfos sis = SegmentInfos.readLatestCommit(dir);
      SegmentCommitInfo sci = sis.info(0);
      try (IndexInput in = dir.openInput(sis.getSegmentsFileName(), IOContext.READONCE)) {
        CodecUtil.checkIndexHeader(
            in,
            "segments",
            0,
            Integer.MAX_VALUE,
            sis.getId(),
            Long.toString(sis.getGeneration(), Character.MAX_RADIX));
        in.readVInt(); // Lucene version: major
        in.readVInt(); // minor
        in.readVInt(); // bugfix
        assertEquals(sis.getIndexCreatedVersionMajor(), in.readVInt()); // undocumented
        assertEquals(sis.getVersion(), CodecUtil.readBELong(in));
        assertEquals(sis.counter, in.readVLong()); // documented as Int32
        assertEquals(1, CodecUtil.readBEInt(in));
        in.readVInt(); // MinSegmentLuceneVersion
        in.readVInt();
        in.readVInt();
        // the documented segment entry
        assertEquals("SegName", sci.info.name, in.readString());
        byte[] id = new byte[16];
        in.readBytes(id, 0, id.length);
        assertArrayEquals("SegID", sci.info.getId(), id);
        assertEquals("SegCodec", sci.info.getCodec().getName(), in.readString());
        assertEquals("DelGen", sci.getDelGen(), CodecUtil.readBELong(in));
        assertEquals("DeletionCount", sci.getDelCount(), CodecUtil.readBEInt(in));
        assertEquals("FieldInfosGen", sci.getFieldInfosGen(), CodecUtil.readBELong(in));
        assertEquals("DocValuesGen", sci.getDocValuesGen(), CodecUtil.readBELong(in));
        assertEquals(
            "UpdatesFiles count",
            sci.getDocValuesUpdatesFiles().size(),
            CodecUtil.readBEInt(in));
        assertEquals(
            "DocValuesOverlays OverlayCount",
            sci.getDocValuesOverlays().size(),
            CodecUtil.readBEInt(in));
        assertEquals("CommitUserData", sis.getUserData(), in.readMapOfStrings());
        assertEquals(in.length() - CodecUtil.footerLength(), in.getFilePointer());
      }
    }
  }
}
