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
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.KnnVectorValues;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.DocAndFloatFeatureBuffer;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.VectorScorer;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.quantization.QuantizedByteVectorValues.ScalarEncoding;

/**
 * With the Lucene104 scalar-quantized format, once the raw float vectors are dropped (the read-only
 * mode that OffHeapScalarQuantizedFloatVectorValues serves), FloatVectorValues#rescorer is meant to
 * score against the dequantized vectors (Lucene104ScalarQuantizedVectorsReader#withQuantized). Its
 * score() does, but its bulk() scorer does not: on an MMapDirectory with the Panama vector provider
 * it scores the quantized bytes of the .veq file as if they were raw float32 vectors.
 *
 * <p>Run with --add-modules jdk.incubator.vector (the default of Lucene's test JVM). Only public
 * API is used; the package only matches the format's own tests.
 */
public class TestDroppedRawRescorerBulk extends LuceneTestCase {
  private static final String FIELD = "v";
  private static final int DIM = 4;
  private static final float[][] DOCS = {
    {0.1f, 0.2f, 0.3f, 0.4f}, {-0.5f, 0.25f, 0.0f, 1.0f}, {0.9f, -0.3f, 0.2f, 0.1f}, {0.3f, 0.3f, -0.6f, 0.0f}
  };
  private static final float[] QUERY = {0.2f, 0.1f, -0.1f, 0.5f};

  /** The failing case: dropped raw vectors, memory-mapped index. */
  public void testRescorerBulkMatchesScoreAfterDroppingRawVectors() throws IOException {
    try (Directory dir = new MMapDirectory(createTempDir())) {
      float[][] scores = rescoreBothWays(dir, true);
      assertArrayEquals(
          "rescorer().bulk() must give the same scores as rescorer().score()",
          scores[0],
          scores[1],
          0f);
    }
  }

  /** Control: the same index with its raw vectors still present. */
  public void testControlRawVectorsPresent() throws IOException {
    try (Directory dir = new MMapDirectory(createTempDir())) {
      float[][] scores = rescoreBothWays(dir, false);
      assertArrayEquals(scores[0], scores[1], 0f);
    }
  }

  /** Control: dropped raw vectors on a heap directory (no MemorySegment access). */
  public void testControlHeapDirectory() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      float[][] scores = rescoreBothWays(dir, true);
      assertArrayEquals(scores[0], scores[1], 0f);
    }
  }

  /** Returns {score() per doc, bulk() per doc}. */
  private static float[][] rescoreBothWays(Directory dir, boolean dropRaw) throws IOException {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(
                TestUtil.alwaysKnnVectorsFormat(
                    new Lucene104HnswScalarQuantizedVectorsFormat(ScalarEncoding.UNSIGNED_BYTE, 16, 100)))
            .setUseCompoundFile(false)
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (float[] v : DOCS) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField(FIELD, v, VectorSimilarityFunction.EUCLIDEAN));
        w.addDocument(doc);
      }
    }
    if (dropRaw) {
      dropRawVectors(dir);
    }
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(1, reader.leaves().size());
      LeafReader leaf = reader.leaves().get(0).reader();
      FloatVectorValues values = leaf.getFloatVectorValues(FIELD);
      float[] viaScore = new float[DOCS.length];
      VectorScorer scorer = values.rescorer(QUERY);
      DocIdSetIterator it = scorer.iterator();
      for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
        viaScore[doc] = scorer.score();
      }
      // score() does score the dequantized vectors, as documented
      KnnVectorValues.DocIndexIterator vit = values.iterator();
      for (int doc = vit.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = vit.nextDoc()) {
        assertEquals(
            VectorSimilarityFunction.EUCLIDEAN.compare(QUERY, values.vectorValue(vit.index())),
            viaScore[doc],
            0f);
      }
      float[] viaBulk = new float[DOCS.length];
      DocAndFloatFeatureBuffer buffer = new DocAndFloatFeatureBuffer();
      values.rescorer(QUERY).bulk(null).nextDocsAndScores(DocIdSetIterator.NO_MORE_DOCS, null, buffer);
      assertEquals(DOCS.length, buffer.size);
      for (int i = 0; i < buffer.size; i++) {
        viaBulk[buffer.docs[i]] = buffer.features[i];
      }
      return new float[][] {viaScore, viaBulk};
    }
  }

  /**
   * Drops the raw vectors as TestLucene104ScalarQuantizedVectorsFormat#simulateEmptyRawVectors does:
   * the .vec data becomes header + footer and its .vemf entry reports zero vectors.
   */
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
