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
package org.apache.lucene.codecs.lucene90;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.packed.BlockPackedReaderIterator;
import org.apache.lucene.util.packed.PackedInts;

/**
 * Parses one Lucene90 term-vectors chunk byte by byte and compares it with the file-format
 * description in the Lucene90TermVectorsFormat class javadoc.
 *
 * <p>The document has three term-vector fields with identical options (offsets, no positions), one
 * term each, so the chunk is: DocBase, ChunkDocs, NumFields (VInt, one doc), FieldNums, FieldNumOffs,
 * Flags, NumTerms, TermLengths, TermFreqs, (no Positions), StartOffsets, Lengths, (no payloads),
 * TermAndPayloads.
 */
public class TestTermVectorsFormatJavadocLayout extends LuceneTestCase {

  public void testChunkLayoutMatchesJavadoc() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setCodec(TestUtil.getDefaultCodec());
      iwc.setUseCompoundFile(false);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
        ft.setStoreTermVectors(true);
        ft.setStoreTermVectorOffsets(true); // offsets WITHOUT positions
        ft.freeze();
        Document doc = new Document();
        doc.add(new Field("a", new CannedTokenStream(new Token("x", 3, 4)), ft));
        doc.add(new Field("b", new CannedTokenStream(new Token("y", 5, 6)), ft));
        doc.add(new Field("c", new CannedTokenStream(new Token("z", 7, 8)), ft));
        w.addDocument(doc);
      }
      List<String> mismatches = new ArrayList<>();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReader leaf = r.leaves().get(0).reader();
        SegmentReader sr = (SegmentReader) leaf;
        String seg = sr.getSegmentName();
        int[] nums = new int[3];
        int k = 0;
        for (String f : new String[] {"a", "b", "c"}) {
          FieldInfo fi = leaf.getFieldInfos().fieldInfo(f);
          nums[k++] = fi.number;
        }
        try (IndexInput in = dir.openInput(seg + ".tvd", newIOContext(random()))) {
          in.seek(CodecUtil.indexHeaderLength("Lucene90TermVectorsData", ""));
          int docBase = in.readVInt();
          int chunkDocs = in.readVInt() >>> 1;
          assertEquals(0, docBase);
          assertEquals(1, chunkDocs);
          int numFields = in.readVInt(); // DocNumFields with ChunkDocs==1: VInt
          assertEquals(3, numFields);
          // FieldNums --> FieldNumDelta^TotalDistinctFields: "a delta-encoded list of the sorted
          // unique field numbers present in the chunk"
          int token = in.readByte() & 0xFF;
          int bits = token & 0x1F;
          int distinct = (token >>> 5) + 1;
          assertEquals(3, distinct);
          PackedInts.ReaderIterator it =
              PackedInts.getReaderIteratorNoHeader(
                  in, PackedInts.Format.PACKED, PackedInts.VERSION_CURRENT, distinct, bits, 1);
          long[] stored = new long[distinct];
          for (int i = 0; i < distinct; i++) {
            stored[i] = it.next();
          }
          long[] deltas = {nums[0], nums[1] - nums[0], nums[2] - nums[1]};
          if (!java.util.Arrays.equals(stored, deltas)) {
            mismatches.add(
                "FieldNums: javadoc says delta-encoded "
                    + java.util.Arrays.toString(deltas)
                    + ", file holds "
                    + java.util.Arrays.toString(stored)
                    + " (the plain field numbers)");
          }
          // FieldNumOffs: the javadoc lists a PackedInts array; the file has a VLong byte length
          // followed by that many bytes (skipped here)
          long fieldNumOffsLen = in.readVLong();
          in.skipBytes(fieldNumOffsLen);
          // Flags --> Bit <FieldFlags>; "Bit ... when true means that fields have the same options
          // for every document in the chunk"; "FieldFlags --> if Bit==1: Flag^TotalDistinctFields"
          int bit = in.readVInt();
          if (bit != 1) {
            mismatches.add(
                "Flags: all fields have the same options, javadoc says Bit==1, file holds Bit=" + bit);
          }
          int flagsLen = in.readVInt();
          in.skipBytes(flagsLen);
          // NumTerms: VInt bitsRequired, VInt length, DirectWriter bytes
          in.readVInt();
          int numTermsLen = in.readVInt();
          in.skipBytes(numTermsLen);
          // TermLengths (prefix, suffix) and TermFreqs: blocks of 64 packed ints, 3 values each
          for (int s = 0; s < 3; s++) {
            BlockPackedReaderIterator bp =
                new BlockPackedReaderIterator(in, PackedInts.VERSION_CURRENT, 64, 3);
            for (int i = 0; i < 3; i++) {
              bp.next();
            }
          }
          // no field has positions: no Positions block.
          // StartOffsets --> (AvgCharsPerTerm^TotalDistinctFields) StartOffsetDelta^TotalOffsets;
          // "They [AvgCharsPerTerm] are not present if no field has both positions and offsets
          // enabled."  Decode StartOffsetDelta from here: start offsets are 3, 5, 7.
          long fp = in.getFilePointer();
          BlockPackedReaderIterator bp =
              new BlockPackedReaderIterator(in, PackedInts.VERSION_CURRENT, 64, 3);
          long[] got = {bp.next(), bp.next(), bp.next()};
          if (got[0] != 3 || got[1] != 5 || got[2] != 7) {
            in.seek(fp + 4L * distinct);
            BlockPackedReaderIterator bp2 =
                new BlockPackedReaderIterator(in, PackedInts.VERSION_CURRENT, 64, 3);
            long[] got2 = {bp2.next(), bp2.next(), bp2.next()};
            mismatches.add(
                "StartOffsets: no field has positions, javadoc says AvgCharsPerTerm is absent, but"
                    + " decoding without it gives "
                    + java.util.Arrays.toString(got)
                    + " and skipping "
                    + distinct
                    + " AvgCharsPerTerm ints gives "
                    + java.util.Arrays.toString(got2)
                    + " (expected [3, 5, 7])");
          }
        }
      }
      assertTrue(String.join("\n", mismatches), mismatches.isEmpty());
    }
  }
}
