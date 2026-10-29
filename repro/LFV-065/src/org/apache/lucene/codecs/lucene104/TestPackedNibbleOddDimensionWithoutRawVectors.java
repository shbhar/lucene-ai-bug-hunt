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
package org.apache.lucene.codecs.lucene104;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.lucene95.OrdToDocDISIReaderConfiguration;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.quantization.QuantizedByteVectorValues.ScalarEncoding;

public class TestPackedNibbleOddDimensionWithoutRawVectors extends LuceneTestCase {

  public void testVectorValueWithOddDimension() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(
                  TestUtil.alwaysKnnVectorsFormat(
                      new Lucene104ScalarQuantizedVectorsFormat(ScalarEncoding.PACKED_NIBBLE)))
              .setUseCompoundFile(false);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField("v", new float[] {0.1f, 0.2f, 0.3f}));
        w.addDocument(doc);
      }
      simulateEmptyRawVectors(dir);
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        float[] v;
        try {
          v = getOnlyLeafReader(reader).getFloatVectorValues("v").vectorValue(0);
        } catch (RuntimeException e) {
          // expected: the dequantized 3-dim vector; actual: IllegalArgumentException from int4Unpack
          throw new AssertionError("expected the dequantized 3-dim vector, got " + e, e);
        }
        assertEquals(3, v.length);
      }
    }
  }

  private static void simulateEmptyRawVectors(Directory dir) throws Exception {
    for (String file : dir.listAll()) {
      if (file.endsWith(".vec")) {
        byte[] header;
        try (IndexInput in = dir.openInput(file, IOContext.DEFAULT)) {
          header = CodecUtil.readIndexHeader(in);
        }
        dir.deleteFile(file);
        try (IndexOutput out = dir.createOutput(file, IOContext.DEFAULT)) {
          out.writeBytes(header, header.length);
          CodecUtil.writeFooter(out);
        }
      } else if (file.endsWith(".vemf")) {
        byte[] header;
        int field, encoding, similarity, dim;
        long offset;
        try (IndexInput in = dir.openInput(file, IOContext.DEFAULT)) {
          header = CodecUtil.readIndexHeader(in);
          field = in.readInt();
          encoding = in.readInt();
          similarity = in.readInt();
          offset = in.readVLong();
          in.readVLong();
          dim = in.readVInt();
        }
        dir.deleteFile(file);
        try (IndexOutput out = dir.createOutput(file, IOContext.DEFAULT)) {
          out.writeBytes(header, header.length);
          out.writeInt(field);
          out.writeInt(encoding);
          out.writeInt(similarity);
          out.writeVLong(offset);
          out.writeVLong(0);
          out.writeVInt(dim);
          out.writeInt(0);
          OrdToDocDISIReaderConfiguration.writeStoredMeta(16, out, null, 0, 0, null);
          out.writeInt(-1);
          CodecUtil.writeFooter(out);
        }
      }
    }
  }
}
