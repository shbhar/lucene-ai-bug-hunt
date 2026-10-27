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
package org.apache.lucene.queries.function;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloat16VectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.queries.function.valuesource.ConstKnnFloat16ValueSource;
import org.apache.lucene.queries.function.valuesource.ConstKnnFloatValueSource;
import org.apache.lucene.queries.function.valuesource.Float16KnnVectorFieldSource;
import org.apache.lucene.queries.function.valuesource.Float16VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * ConstKnnFloatValueSource rejects a non-finite vector (VectorUtil.checkFinite); its float16 twin
 * ConstKnnFloat16ValueSource accepts one, and the similarity function query then scores with it.
 */
public class TestConstKnnFloat16ValueSourceValidation extends LuceneTestCase {

  private static final short POSITIVE_INFINITY_HALF = (short) 0x7C00;
  private static final short NAN_HALF = (short) 0x7E00;

  public void testFloat32TwinRejectsNonFinite() {
    expectThrows(
        IllegalArgumentException.class,
        () -> new ConstKnnFloatValueSource(new float[] {Float.POSITIVE_INFINITY, 1f}));
    expectThrows(
        IllegalArgumentException.class,
        () -> new ConstKnnFloatValueSource(new float[] {Float.NaN, 1f}));
  }

  public void testFloat16RejectsInfinity() {
    expectThrows(
        IllegalArgumentException.class,
        () -> new ConstKnnFloat16ValueSource(new short[] {POSITIVE_INFINITY_HALF, Float.floatToFloat16(1f)}));
  }

  public void testFloat16RejectsNaN() throws Exception {
    short[] nanQuery = {NAN_HALF, Float.floatToFloat16(1f)};
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        Document doc = new Document();
        doc.add(
            new KnnFloat16VectorField(
                "v",
                new short[] {Float.floatToFloat16(1f), Float.floatToFloat16(2f)},
                VectorSimilarityFunction.EUCLIDEAN));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        ConstKnnFloat16ValueSource query;
        try {
          query = new ConstKnnFloat16ValueSource(nanQuery);
        } catch (IllegalArgumentException expected) {
          return; // documented behaviour of the float32 twin
        }
        FunctionQuery fq =
            new FunctionQuery(
                new Float16VectorSimilarityFunction(
                    VectorSimilarityFunction.EUCLIDEAN, query, new Float16KnnVectorFieldSource("v")));
        TopDocs td = searcher.search(fq, 1);
        fail(
            "a NaN float16 query vector was accepted; the similarity score is "
                + td.scoreDocs[0].score);
      }
    }
  }
}
