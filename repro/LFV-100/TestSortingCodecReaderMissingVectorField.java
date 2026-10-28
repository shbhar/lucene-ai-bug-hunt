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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104HnswScalarQuantizedVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104ScalarQuantizedVectorsFormat;
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnByteVectorField;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Re-sorting an index with SortingCodecReader and addIndexes (the use the class javadoc describes)
 * fails when the vectors format is scalar quantized and one source segment has vectors, but not
 * for every float vector field. The quantized writer's merge asks each reader for the field's
 * vector values and skips a null answer; SortingCodecReader's vectors reader wraps that null and
 * fails (AssertionError with assertions on, NullPointerException without).
 */
public class TestSortingCodecReaderMissingVectorField extends LuceneTestCase {

  private void resort(KnnVectorsFormat format) throws Exception {
    Sort sort = new Sort(new SortField("sort", SortField.Type.LONG));
    try (Directory src = newDirectory();
        Directory dst = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(
              src,
              new IndexWriterConfig()
                  .setCodec(TestUtil.alwaysKnnVectorsFormat(format))
                  .setMergePolicy(NoMergePolicy.INSTANCE))) {
        // segment 1: docs with a float vector and a byte vector
        for (int i = 0; i < 3; i++) {
          Document d = new Document();
          d.add(new StringField("id", "a" + i, Field.Store.YES));
          d.add(new NumericDocValuesField("sort", 10 - i));
          d.add(new KnnFloatVectorField("float_vec", new float[] {i + 1, 1f, 0.5f}));
          d.add(new KnnByteVectorField("byte_vec", new byte[] {(byte) i, 1, 2}));
          w.addDocument(d);
        }
        w.commit();
        // segment 2: docs with only the byte vector
        for (int i = 0; i < 3; i++) {
          Document d = new Document();
          d.add(new StringField("id", "b" + i, Field.Store.YES));
          d.add(new NumericDocValuesField("sort", 5 - i));
          d.add(new KnnByteVectorField("byte_vec", new byte[] {(byte) i, 2, 1}));
          w.addDocument(d);
        }
        w.commit();
      }
      try (IndexWriter w =
              new IndexWriter(
                  dst,
                  new IndexWriterConfig()
                      .setCodec(TestUtil.alwaysKnnVectorsFormat(format))
                      .setIndexSort(sort)
                      .setMergeScheduler(new SerialMergeScheduler()));
          DirectoryReader r = DirectoryReader.open(src)) {
        List<CodecReader> sorted = new ArrayList<>();
        for (LeafReaderContext ctx : r.leaves()) {
          sorted.add(SortingCodecReader.wrap((CodecReader) ctx.reader(), sort));
        }
        w.addIndexes(sorted.toArray(new CodecReader[0]));
        w.commit();
        try (DirectoryReader out = DirectoryReader.open(w)) {
          assertEquals(6, out.numDocs());
          assertEquals(3, getOnlyLeafReader(out).getFloatVectorValues("float_vec").size());
        }
      }
    }
  }

  public void testFlatScalarQuantized() throws Exception {
    resort(new Lucene104ScalarQuantizedVectorsFormat());
  }

  public void testHnswScalarQuantized() throws Exception {
    resort(new Lucene104HnswScalarQuantizedVectorsFormat());
  }

  /** Control: the default (non-quantized) HNSW format re-sorts the same index fine. */
  public void testHnswFloat() throws Exception {
    resort(new Lucene99HnswVectorsFormat());
  }
}
