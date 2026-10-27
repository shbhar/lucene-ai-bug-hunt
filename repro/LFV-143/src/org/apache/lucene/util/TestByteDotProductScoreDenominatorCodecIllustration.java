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
package org.apache.lucene.util;

import java.io.IOException;
import java.util.Arrays;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.KnnVectorsWriter;
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat;
import org.apache.lucene.codecs.perfield.PerFieldKnnVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnByteVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnByteVectorQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * ILLUSTRATION, NOT A CONTRACT TEST. Shows that the TestByteDotProductScoreDenominator defect propagates to kNN search when a
 * KnnVectorsFormat declares a dimension limit above the overflow threshold. The format used here
 * wraps the stock Lucene99HnswVectorsFormat, which itself declares getMaxDimensions() = 1024
 * (KnnVectorsFormat.DEFAULT_MAX_DIMENSIONS), and the KnnByteVectorField javadoc still says that a
 * dimension above 1024 throws; so this configuration claims more than the stock format supports.
 * A non-stock format that really supports such dimensions and scores byte vectors through
 * VectorSimilarityFunction would behave the same way.
 */
public class TestByteDotProductScoreDenominatorCodecIllustration extends LuceneTestCase {

  private static byte[] filled(int n, int value) {
    byte[] v = new byte[n];
    Arrays.fill(v, (byte) value);
    return v;
  }

  /**
   * Through the codec: a KnnVectorsFormat that raises getMaxDimensions (the documented per-codec
   * limit) and delegates to the default HNSW format. On MMapDirectory, so that the Panama
   * MemorySegment byte scorer is used when the vector module is enabled.
   */
  public void testKnnByteVectorQueryDotProductReturnsOppositeFirst() throws IOException {
    int n = 1 << 16;
    Codec codec = maxDimensionsCodec(n);
    try (Directory dir = new MMapDirectory(createTempDir("dotdenominator"))) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(codec))) {
        Document opposite = new Document();
        opposite.add(new StoredField("id", "opposite"));
        opposite.add(new KnnByteVectorField("v", filled(n, -1), VectorSimilarityFunction.DOT_PRODUCT));
        w.addDocument(opposite);
        Document same = new Document();
        same.add(new StoredField("id", "same"));
        same.add(new KnnByteVectorField("v", filled(n, 1), VectorSimilarityFunction.DOT_PRODUCT));
        w.addDocument(same);
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        TopDocs td = s.search(new KnnByteVectorQuery("v", filled(n, 1), 1), 1);
        assertEquals(1, td.scoreDocs.length);
        String top = s.storedFields().document(td.scoreDocs[0].doc).get("id");
        assertEquals("most similar document (score " + td.scoreDocs[0].score + ")", "same", top);
      }
    }
  }

  private static Codec maxDimensionsCodec(int maxDims) {
    Codec base = TestUtil.getDefaultCodec();
    KnnVectorsFormat hnsw = new Lucene99HnswVectorsFormat();
    KnnVectorsFormat raised =
        new KnnVectorsFormat(hnsw.getName()) {
          @Override
          public KnnVectorsWriter fieldsWriter(SegmentWriteState state) throws IOException {
            return hnsw.fieldsWriter(state);
          }

          @Override
          public KnnVectorsReader fieldsReader(SegmentReadState state) throws IOException {
            return hnsw.fieldsReader(state);
          }

          @Override
          public int getMaxDimensions(String fieldName) {
            return maxDims;
          }
        };
    return new FilterCodec(base.getName(), base) {
      @Override
      public KnnVectorsFormat knnVectorsFormat() {
        return new PerFieldKnnVectorsFormat() {
          @Override
          public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
            return raised;
          }
        };
      }
    };
  }
}
