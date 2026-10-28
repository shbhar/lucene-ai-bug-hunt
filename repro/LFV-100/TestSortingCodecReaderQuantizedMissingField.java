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
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnByteVectorField;
import org.apache.lucene.document.KnnFloat16VectorField;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Re-sorts an index as the {@link SortingCodecReader} javadoc describes: wrap every segment and
 * pass them all to {@link IndexWriter#addIndexes(CodecReader...)} on a fresh, index-sorted writer.
 * One segment has vectors for field "other" but none for field "vec". The re-sort must succeed
 * whatever the vectors format of "vec".
 */
public class TestSortingCodecReaderQuantizedMissingField extends LuceneTestCase {

  private static final Sort SORT = new Sort(new SortField("sort", SortField.Type.LONG));

  /** A production codec that uses {@code format} for every vector field. */
  private static Codec codec(KnnVectorsFormat format) {
    return new Lucene104Codec() {
      @Override
      public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
        return format;
      }
    };
  }

  /** One segment of 3 docs in descending sort order, so the sort reorders it. */
  private static void addSegment(IndexWriter w, boolean withVec, boolean fp16) throws IOException {
    for (int i = 0; i < 3; i++) {
      Document doc = new Document();
      doc.add(new NumericDocValuesField("sort", 3 - i));
      doc.add(new KnnByteVectorField("other", new byte[] {(byte) i, 1, 2}));
      if (withVec) {
        float[] v = {i + 1, 1f, 0.5f};
        if (fp16) {
          short[] h = new short[v.length];
          for (int j = 0; j < v.length; j++) {
            h[j] = Float.floatToFloat16(v[j]);
          }
          doc.add(new KnnFloat16VectorField("vec", h, VectorSimilarityFunction.EUCLIDEAN));
        } else {
          doc.add(new KnnFloatVectorField("vec", v));
        }
      }
      w.addDocument(doc);
    }
    w.commit();
  }

  private void resort(KnnVectorsFormat format, boolean fp16, boolean lackingSegmentFirst)
      throws IOException {
    try (Directory src = newDirectory();
        Directory dst = newDirectory()) {
      IndexWriterConfig srcConfig =
          new IndexWriterConfig().setCodec(codec(format)).setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(src, srcConfig)) {
        addSegment(w, lackingSegmentFirst == false, fp16);
        addSegment(w, lackingSegmentFirst, fp16);
      }
      IndexWriterConfig dstConfig =
          new IndexWriterConfig()
              .setCodec(codec(format))
              .setIndexSort(SORT)
              .setMergeScheduler(new SerialMergeScheduler());
      try (DirectoryReader r = DirectoryReader.open(src);
          IndexWriter w = new IndexWriter(dst, dstConfig)) {
        assertEquals(2, r.leaves().size());
        List<CodecReader> wrapped = new ArrayList<>();
        for (LeafReaderContext ctx : r.leaves()) {
          wrapped.add(SortingCodecReader.wrap((CodecReader) ctx.reader(), SORT));
        }
        w.addIndexes(wrapped.toArray(new CodecReader[0])); // fails on quantized formats
        w.commit();
        try (DirectoryReader out = DirectoryReader.open(w)) {
          assertEquals(6, out.numDocs());
          LeafReader leaf = getOnlyLeafReader(out);
          KnnVectorValues vec =
              fp16 ? leaf.getFloat16VectorValues("vec") : leaf.getFloatVectorValues("vec");
          assertEquals(3, vec.size());
        }
      }
    }
  }

  public void testFlatScalarQuantized() throws Exception {
    resort(new Lucene104ScalarQuantizedVectorsFormat(), false, false);
  }

  public void testHnswScalarQuantized() throws Exception {
    resort(new Lucene104HnswScalarQuantizedVectorsFormat(), false, false);
  }

  /** The lacking segment comes first, so the first centroid loop meets it. */
  public void testHnswScalarQuantizedLackingSegmentFirst() throws Exception {
    resort(new Lucene104HnswScalarQuantizedVectorsFormat(), false, true);
  }

  public void testHnswScalarQuantizedFloat16() throws Exception {
    resort(new Lucene104HnswScalarQuantizedVectorsFormat(), true, false);
  }

  /** Control: the default, non-quantized HNSW format re-sorts the same index. */
  public void testNonQuantizedControl() throws Exception {
    resort(new Lucene99HnswVectorsFormat(), false, false);
  }
}
