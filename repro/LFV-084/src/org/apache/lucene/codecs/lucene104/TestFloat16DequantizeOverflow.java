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
import org.apache.lucene.index.Float16VectorValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.KnnVectorValues;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloat16VectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RescoreTopNQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.quantization.QuantizedByteVectorValues.ScalarEncoding;

/**
 * With the Lucene104 scalar-quantized format and FLOAT16 vectors, once the raw fp16 vectors are
 * dropped, Float16VectorValues#vectorValue reconstructs each vector by dequantizing it
 * (OffHeapScalarQuantizedFloat16VectorValues, javadoc: "reconstructs float16 values by dequantizing
 * them, with some precision loss"). The reconstruction is narrowed to fp16 without saturation
 * (OptimizedScalarQuantizer#deQuantize(byte[], short[], ...)), so a finite vector that
 * KnnFloat16VectorField accepted can come back with an infinite component, and full-precision
 * rescoring then ranks that document as if it were infinitely far away.
 *
 * <p>Only public API is used; the package only matches the format's own tests.
 */
public class TestFloat16DequantizeOverflow extends LuceneTestCase {
  private static final String FIELD = "v";
  // Legal fp16 vectors (all components finite and well inside +-65504).
  private static final float[][] DOCS = {
    {60000f, 49984f, 0f, 0f}, {20000f, -49984f, 0f, 0f}, {40000f, 0f, 0f, 0f}
  };

  private static short[] fp16(float[] v) {
    short[] s = new short[v.length];
    for (int i = 0; i < v.length; i++) {
      s[i] = Float.floatToFloat16(v[i]);
      assertEquals("test vectors must be exact in fp16", v[i], Float.float16ToFloat(s[i]), 0f);
    }
    return s;
  }

  private static Directory buildDroppedRawIndex() throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = config();
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (float[] v : DOCS) {
        Document doc = new Document();
        doc.add(new KnnFloat16VectorField(FIELD, fp16(v), VectorSimilarityFunction.EUCLIDEAN));
        w.addDocument(doc);
      }
    }
    dropRawVectors(dir);
    return dir;
  }

  /** The reconstructed vectors must be finite, like every vector the field API accepts. */
  public void testDequantizedFloat16VectorsAreFinite() throws IOException {
    try (Directory dir = buildDroppedRawIndex();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      LeafReader leaf = reader.leaves().get(0).reader();
      Float16VectorValues values = leaf.getFloat16VectorValues(FIELD);
      assertEquals(DOCS.length, values.size());
      KnnVectorValues.DocIndexIterator it = values.iterator();
      for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
        short[] v = values.vectorValue(it.index());
        for (int i = 0; i < v.length; i++) {
          float x = Float.float16ToFloat(v[i]);
          assertTrue(
              "doc " + doc + " dim " + i + ": stored " + DOCS[doc][i] + ", reconstructed " + x,
              Float.isFinite(x));
        }
      }
    }
  }

  /** Full-precision rescoring of the exact vector of doc 0 must rank doc 0 first. */
  public void testFullPrecisionRescoreOfExactMatch() throws IOException {
    try (Directory dir = buildDroppedRawIndex();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      short[] query = fp16(DOCS[0]);
      Query rescored =
          RescoreTopNQuery.createFullPrecisionRescorerQuery(
              new KnnFloat16VectorQuery(FIELD, query, DOCS.length), query, FIELD, DOCS.length);
      TopDocs top = searcher.search(rescored, DOCS.length);
      assertEquals(DOCS.length, top.scoreDocs.length);
      assertEquals(
          "top hit for doc 0's own vector; scores " + java.util.Arrays.toString(top.scoreDocs),
          0,
          top.scoreDocs[0].doc);
    }
  }

  private static IndexWriterConfig config() {
    return new IndexWriterConfig()
        .setCodec(
            TestUtil.alwaysKnnVectorsFormat(
                new Lucene104HnswScalarQuantizedVectorsFormat(
                    ScalarEncoding.SINGLE_BIT_QUERY_NIBBLE, 16, 100)))
        .setUseCompoundFile(false)
        .setMergePolicy(NoMergePolicy.INSTANCE);
  }

  /** Drops the raw vectors as TestLucene104ScalarQuantizedVectorsFormat#simulateEmptyRawVectors does. */
  private static void dropRawVectors(Directory dir) throws IOException {
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
