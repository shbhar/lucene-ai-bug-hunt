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
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloat16VectorField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * FLOAT16 variant (main only): two fp16 vector fields, segment 1 has only "a". Re-sort through
 * SortingCodecReader + addIndexes. Only public API is used.
 */
public class TestResortQuantizedMissingFieldFp16 extends LuceneTestCase {

  private static final int DIM = 8;
  private static final int N = 20;

  private static Codec codecWith(KnnVectorsFormat format) {
    return new Lucene104Codec() {
      @Override
      public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
        return format;
      }
    };
  }

  private short[] randomVector() {
    short[] v = new short[DIM];
    for (int i = 0; i < DIM; i++) {
      v[i] = Float.floatToFloat16(random().nextFloat() * 2f - 1f);
    }
    return v;
  }

  private void runCase(KnnVectorsFormat format) throws IOException {
    Codec codec = codecWith(format);
    try (Directory src = newDirectory();
        Directory dst = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig().setCodec(codec).setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(src, iwc)) {
        int rank = 0;
        for (int seg = 0; seg < 2; seg++) {
          for (int i = 0; i < N; i++, rank++) {
            Document doc = new Document();
            doc.add(new NumericDocValuesField("rank", rank));
            doc.add(
                new KnnFloat16VectorField("a", randomVector(), VectorSimilarityFunction.EUCLIDEAN));
            if (seg == 0) {
              doc.add(
                  new KnnFloat16VectorField(
                      "b", randomVector(), VectorSimilarityFunction.EUCLIDEAN));
            }
            w.addDocument(doc);
          }
          w.commit();
        }
      }
      try (DirectoryReader r = DirectoryReader.open(src)) {
        assertEquals(2, r.leaves().size());
        assertNull(r.leaves().get(1).reader().getFieldInfos().fieldInfo("b"));
        List<CodecReader> readers = new ArrayList<>();
        for (LeafReaderContext ctx : r.leaves()) {
          readers.add(SortingCodecReader.wrap((CodecReader) ctx.reader(), ResortCase.SORT));
        }
        IndexWriterConfig iwc2 = new IndexWriterConfig().setCodec(codec);
        iwc2.setIndexSort(ResortCase.SORT);
        try (IndexWriter w = new IndexWriter(dst, iwc2)) {
          w.addIndexes(readers.toArray(new CodecReader[0]));
          w.commit();
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dst)) {
        assertEquals(2 * N, r.numDocs());
        int a = 0;
        int b = 0;
        for (LeafReaderContext ctx : r.leaves()) {
          a += ctx.reader().getFloat16VectorValues("a").size();
          Float16VectorValues bv = ctx.reader().getFloat16VectorValues("b");
          b += bv == null ? 0 : bv.size();
        }
        assertEquals(2 * N, a);
        assertEquals(N, b);
      }
    }
  }

  public void testHnswScalarQuantizedFp16ResortSparseField() throws Exception {
    runCase(new Lucene104HnswScalarQuantizedVectorsFormat());
  }

  public void testControlHnswFloatFp16ResortSparseField() throws Exception {
    runCase(new Lucene99HnswVectorsFormat());
  }
}
