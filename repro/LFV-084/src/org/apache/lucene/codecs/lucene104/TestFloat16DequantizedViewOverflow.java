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

import java.io.IOException;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.lucene95.OrdToDocDISIReaderConfiguration;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloat16VectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestFloat16DequantizedViewOverflow extends LuceneTestCase {

  public void testDequantizedValuesAreFinite() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(
                  TestUtil.alwaysKnnVectorsFormat(new Lucene104ScalarQuantizedVectorsFormat()))
              .setUseCompoundFile(false);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (float[] v : new float[][] {{65504f, -65504f, 0f}, {0f, 0f, 0f}}) {
          short[] h = new short[v.length];
          for (int i = 0; i < v.length; i++) {
            h[i] = Float.floatToFloat16(v[i]);
          }
          Document doc = new Document();
          doc.add(new KnnFloat16VectorField("v", h, VectorSimilarityFunction.EUCLIDEAN));
          w.addDocument(doc);
        }
      }
      simulateEmptyRawVectors(dir);
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        short[] v = r.leaves().get(0).reader().getFloat16VectorValues("v").vectorValue(0);
        float y = Float.float16ToFloat(v[1]);
        assertTrue("expected a finite approximation of -65504, got " + y, Float.isFinite(y));
      }
    }
  }

  private static void simulateEmptyRawVectors(Directory dir) throws IOException {
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
