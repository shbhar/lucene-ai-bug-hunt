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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104Codec;
import org.apache.lucene.codecs.lucene104.Lucene104HnswScalarQuantizedVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104ScalarQuantizedVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndexWriter#addIndexes(CodecReader...) does not take ownership of its readers ("The provided
 * IndexReaders are not closed"), and SlowCodecReaderWrapper "wraps arbitrary readers for merging".
 * Merging into a scalar-quantized vector field must therefore leave the source reader's vectors
 * as they were. With the Lucene104 scalar-quantized formats and a FLOAT32 field whose similarity is
 * not COSINE, the merge quantizes the arrays returned by the source's FloatVectorValues#vectorValue
 * in place (OptimizedScalarQuantizer#scalarQuantize subtracts the centroid from its argument).
 */
public class TestQuantizedMergeSourceVectorsUnchanged extends LuceneTestCase {

  private static final float[] VECTOR = {1f, 2f, 3f, 4f};
  private static final float[] QUERY = {0.5f, 1f, -1f, 2f};

  private static Codec codec(KnnVectorsFormat format) {
    return new Lucene104Codec() {
      @Override
      public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
        return format;
      }
    };
  }

  private static IndexWriterConfig config(KnnVectorsFormat format) {
    return new IndexWriterConfig().setCodec(codec(format));
  }

  /** A one-document MemoryIndex holding VECTOR, exposed as a LeafReader. */
  private static LeafReader memoryIndexReader(VectorSimilarityFunction similarity) {
    MemoryIndex memoryIndex = new MemoryIndex();
    memoryIndex.addField(new KnnFloatVectorField("v", VECTOR.clone(), similarity), null);
    return (LeafReader) memoryIndex.createSearcher().getIndexReader().leaves().get(0).reader();
  }

  /** The MemoryIndex still holds VECTOR after it was added to a quantized index. */
  public void testSourceReaderVectorUnchanged() throws IOException {
    for (VectorSimilarityFunction similarity :
        new VectorSimilarityFunction[] {
          VectorSimilarityFunction.EUCLIDEAN, VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT
        }) {
      for (KnnVectorsFormat format :
          new KnnVectorsFormat[] {
            new Lucene104ScalarQuantizedVectorsFormat(), new Lucene104HnswScalarQuantizedVectorsFormat()
          }) {
        LeafReader source = memoryIndexReader(similarity);
        try (Directory dir = newDirectory();
            IndexWriter writer = new IndexWriter(dir, config(format))) {
          writer.addIndexes(SlowCodecReaderWrapper.wrap(source));
          writer.commit();
        }
        assertArrayEquals(
            similarity + " " + format + ": the source reader's vector changed",
            VECTOR,
            source.getFloatVectorValues("v").vectorValue(0),
            0f);
      }
    }
  }

  /** Adding the same reader to a second index stores the original vector there as well. */
  public void testSecondIndexStoresOriginalVector() throws IOException {
    LeafReader source = memoryIndexReader(VectorSimilarityFunction.EUCLIDEAN);
    KnnVectorsFormat format = new Lucene104ScalarQuantizedVectorsFormat();
    try (Directory first = newDirectory();
        Directory second = newDirectory()) {
      for (Directory dir : new Directory[] {first, second}) {
        try (IndexWriter writer = new IndexWriter(dir, config(format))) {
          writer.addIndexes(SlowCodecReaderWrapper.wrap(source));
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(second)) {
        FloatVectorValues stored = reader.leaves().get(0).reader().getFloatVectorValues("v");
        assertArrayEquals(
            "full-precision vector stored by the second addIndexes", VECTOR, stored.vectorValue(0), 0f);
      }
    }
  }

  /**
   * Re-embedding through a FilterLeafReader: the new vectors come from a caller-owned list
   * (FloatVectorValues#fromFloats) and are written to a quantized index with addIndexes. The list
   * must still hold the new vectors afterwards.
   */
  public void testFilterReaderSuppliedVectorsUnchanged() throws IOException {
    float[][] embeddings = {{1f, 2f, 3f, 4f}, {4f, 3f, 2f, 1f}};
    List<float[]> newVectors = new ArrayList<>();
    for (float[] e : embeddings) {
      newVectors.add(e.clone());
    }
    try (Directory source = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(source, new IndexWriterConfig())) {
        for (int i = 0; i < newVectors.size(); i++) {
          Document doc = new Document();
          doc.add(new KnnFloatVectorField("v", new float[] {0f, 0f, 0f, 1f}));
          writer.addDocument(doc);
        }
        writer.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(source)) {
        LeafReader reembedded =
            new FilterLeafReader(reader.leaves().get(0).reader()) {
              @Override
              public FloatVectorValues getFloatVectorValues(String field) {
                return FloatVectorValues.fromFloats(newVectors, 4);
              }

              @Override
              public CacheHelper getCoreCacheHelper() {
                return null;
              }

              @Override
              public CacheHelper getReaderCacheHelper() {
                return null;
              }
            };
        try (Directory target = newDirectory();
            IndexWriter writer =
                new IndexWriter(target, config(new Lucene104ScalarQuantizedVectorsFormat()))) {
          writer.addIndexes(SlowCodecReaderWrapper.wrap(reembedded));
        }
      }
    }
    for (int i = 0; i < embeddings.length; i++) {
      assertArrayEquals("caller-owned vector " + i, embeddings[i], newVectors.get(i), 0f);
    }
  }

  /** Two documents with the same vector get the same quantized score. */
  public void testSameReaderTwiceScoresEqually() throws IOException {
    LeafReader source = memoryIndexReader(VectorSimilarityFunction.EUCLIDEAN);
    KnnVectorsFormat format = new Lucene104ScalarQuantizedVectorsFormat();
    try (Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, config(format))) {
        CodecReader wrapped = SlowCodecReaderWrapper.wrap(source);
        writer.addIndexes(wrapped, wrapped);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        FloatVectorValues stored = reader.leaves().get(0).reader().getFloatVectorValues("v");
        assertArrayEquals(VECTOR, stored.vectorValue(0), 0f);
        assertArrayEquals(VECTOR, stored.copy().vectorValue(1), 0f);
        TopDocs hits = new IndexSearcher(reader).search(new KnnFloatVectorQuery("v", QUERY, 2), 2);
        assertEquals(2, hits.scoreDocs.length);
        ScoreDoc a = hits.scoreDocs[0];
        ScoreDoc b = hits.scoreDocs[1];
        assertEquals(
            "identical vectors, docs " + a.doc + " and " + b.doc + " (exact score "
                + VectorSimilarityFunction.EUCLIDEAN.compare(VECTOR, QUERY)
                + ")",
            a.score,
            b.score,
            0f);
      }
    }
  }
}
