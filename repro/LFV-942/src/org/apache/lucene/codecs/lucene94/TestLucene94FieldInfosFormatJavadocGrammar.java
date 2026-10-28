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
package org.apache.lucene.codecs.lucene94;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * The class javadoc of {@link Lucene94FieldInfosFormat} documents the .fnm file as
 *
 * <pre>
 * FieldInfos (.fnm) --&gt; Header,FieldsCount, &lt;FieldName,FieldNumber,
 *   FieldBits,DocValuesBits,DocValuesGen,Attributes,DimensionCount,DimensionNumBytes&gt;
 *   <sup>FieldsCount</sup>,Footer
 * </pre>
 *
 * with value tables for FieldBits (0x1, 0x2, 0x4), IndexOptions (0..4), DocValuesBits (two
 * four-bit integers, each 0..3) and VectorEncoding (0..1). These tests parse a .fnm file written by
 * the default codec with that grammar and compare it with what the real reader returns. Only public
 * API is used.
 */
public class TestLucene94FieldInfosFormatJavadocGrammar extends LuceneTestCase {

  private static Directory index(Document... docs) throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setUseCompoundFile(false)
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setSoftDeletesField("soft");
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (Document d : docs) {
        w.addDocument(d);
      }
    }
    return dir;
  }

  private static byte[] segmentId(Directory dir) throws Exception {
    return SegmentInfos.readLatestCommit(dir).info(0).info.getId();
  }

  private static FieldInfos realFieldInfos(Directory dir) throws Exception {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      return r.leaves().get(0).reader().getFieldInfos();
    }
  }

  /** Parse the .fnm of a one-field segment with the documented per-field sequence. */
  public void testDocumentedSequenceParsesWrittenFile() throws Exception {
    Document doc = new Document();
    doc.add(new TextField("body", "hello world", Field.Store.NO));
    try (Directory dir = index(doc)) {
      FieldInfo real = realFieldInfos(dir).fieldInfo("body");
      try (IndexInput in = dir.openInput("_0.fnm", IOContext.READONCE)) {
        CodecUtil.checkIndexHeader(
            in, "Lucene94FieldInfos", 0, Integer.MAX_VALUE, segmentId(dir), "");
        assertEquals("FieldsCount", 1, in.readVInt());
        assertEquals("FieldName", "body", in.readString());
        assertEquals("FieldNumber", real.number, in.readVInt());
        // FieldBits: 0x1 term vectors, 0x2 omit norms, 0x4 payloads
        assertEquals("FieldBits", 0, in.readByte());
        // DocValuesBits: low nibble = DocValues type, 0 means "no DocValues for this field"
        int docValuesBits = in.readByte() & 0xFF;
        assertEquals(
            "DocValuesBits low nibble for a field without doc values (DocValuesType "
                + real.getDocValuesType()
                + ")",
            0,
            docValuesBits & 0x0F);
        assertEquals("DocValuesGen", real.getDocValuesGen(), in.readLong());
        assertEquals("Attributes", real.attributes(), in.readMapOfStrings());
        assertEquals("DimensionCount", real.getPointDimensionCount(), in.readVInt());
        assertEquals("DimensionNumBytes", real.getPointNumBytes(), in.readVInt());
        assertEquals(
            "the grammar should end at the footer",
            in.length() - CodecUtil.footerLength(),
            in.getFilePointer());
      }
    }
  }

  /**
   * Read every per-field byte at the position the writer puts it (the order of {@code
   * Lucene94FieldInfosFormat#write}, which the reader mirrors) and check it against the value tables
   * of the javadoc.
   */
  public void testWrittenValuesAreInDocumentedRanges() throws Exception {
    Document doc = new Document();
    doc.add(new StringField("id", "1", Field.Store.NO));
    doc.add(new SortedSetDocValuesField("tags", new BytesRef("x")));
    doc.add(new SortedNumericDocValuesField("nums", 3));
    Document doc2 = new Document();
    doc2.add(new NumericDocValuesField("soft", 1));
    try (Directory dir = index(doc, doc2)) {
      FieldInfos real = realFieldInfos(dir);
      List<String> violations = new ArrayList<>();
      try (IndexInput in = dir.openInput("_0.fnm", IOContext.READONCE)) {
        CodecUtil.checkIndexHeader(
            in, "Lucene94FieldInfos", 0, Integer.MAX_VALUE, segmentId(dir), "");
        int count = in.readVInt();
        for (int i = 0; i < count; i++) {
          String name = in.readString();
          in.readVInt(); // FieldNumber
          int fieldBits = in.readByte() & 0xFF;
          int indexOptions = in.readByte() & 0xFF;
          int docValues = in.readByte() & 0xFF;
          in.readByte(); // doc values skip index type (undocumented)
          in.readLong(); // DocValuesGen
          in.readMapOfStrings(); // Attributes
          int pointDims = in.readVInt();
          if (pointDims != 0) {
            in.readVInt(); // point index dimension count (undocumented)
            in.readVInt(); // point num bytes
          }
          in.readVInt(); // vector dimension
          int vectorEncoding = in.readByte() & 0xFF;
          in.readByte(); // vector similarity function
          String type = real.fieldInfo(name).getDocValuesType().toString();
          if ((fieldBits & ~0x7) != 0) {
            violations.add(
                name + ": FieldBits 0x" + Integer.toHexString(fieldBits) + " uses undocumented bits");
          }
          if (indexOptions > 4) {
            violations.add(name + ": IndexOptions " + indexOptions + " not in documented 0..4");
          }
          if ((docValues & 0x0F) > 3) {
            violations.add(
                name + ": DocValues code " + docValues + " (" + type + ") not in documented 0..3");
          }
          if (vectorEncoding > 1) {
            violations.add(name + ": VectorEncoding " + vectorEncoding + " not in documented 0..1");
          }
        }
        assertEquals(in.length() - CodecUtil.footerLength(), in.getFilePointer());
      }
      assertEquals("written values outside the documented tables", List.of(), violations);
    }
  }
}
