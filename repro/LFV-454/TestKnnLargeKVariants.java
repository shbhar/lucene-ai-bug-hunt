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
package org.apache.lucene.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnByteVectorField;
import org.apache.lucene.document.KnnFloat16VectorField;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * k = Integer.MAX_VALUE over tiny indexes for the query variants that share the per-segment
 * collector path: byte, float16, several segments (optimistic re-entry), patience and seeded. Each
 * search should return every document.
 */
public class TestKnnLargeKVariants extends LuceneTestCase {

  /** Indexes {@code segments} segments of 3 documents, with float, byte and float16 vectors. */
  private static Directory index(int segments) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int s = 0; s < segments; s++) {
        for (int i = 0; i < 3; i++) {
          Document doc = new Document();
          doc.add(new KnnFloatVectorField("v", new float[] {i, 1f}));
          doc.add(new KnnByteVectorField("b", new byte[] {(byte) i, 1}));
          doc.add(
              new KnnFloat16VectorField(
                  "h",
                  new short[] {Float.floatToFloat16(i), Float.floatToFloat16(1f)},
                  VectorSimilarityFunction.EUCLIDEAN));
          doc.add(new StringField("tag", "x", Field.Store.NO));
          w.addDocument(doc);
        }
        w.commit();
      }
    }
    return dir;
  }

  private static int hits(int segments, Query q) throws Exception {
    try (Directory dir = index(segments);
        IndexReader reader = DirectoryReader.open(dir)) {
      assertEquals(segments, reader.leaves().size());
      IndexSearcher searcher = new IndexSearcher(reader); // plain searcher, no executor
      return searcher.search(q, 100).scoreDocs.length;
    }
  }

  private static void expectAll(String what, int segments, Query q) throws Exception {
    try {
      assertEquals(what, 3 * segments, hits(segments, q));
    } catch (IllegalArgumentException | OutOfMemoryError e) {
      throw new AssertionError(what + " threw " + e, e);
    }
  }

  private static KnnFloatVectorQuery floatQuery(int k) {
    return new KnnFloatVectorQuery("v", new float[] {1f, 1f}, k);
  }

  public void testByteKMaxValue() throws Exception {
    expectAll(
        "KnnByteVectorQuery k=MAX_VALUE",
        1,
        new KnnByteVectorQuery("b", new byte[] {1, 1}, Integer.MAX_VALUE));
  }

  public void testFloat16KMaxValue() throws Exception {
    short[] target = {Float.floatToFloat16(1f), Float.floatToFloat16(1f)};
    expectAll(
        "KnnFloat16VectorQuery k=MAX_VALUE",
        1,
        new KnnFloat16VectorQuery("h", target, Integer.MAX_VALUE));
  }

  public void testFourSegmentsKMaxValue() throws Exception {
    expectAll("KnnFloatVectorQuery, 4 segments, k=MAX_VALUE", 4, floatQuery(Integer.MAX_VALUE));
  }

  public void testPatienceKMaxValue() throws Exception {
    expectAll(
        "PatienceKnnVectorQuery k=MAX_VALUE",
        1,
        PatienceKnnVectorQuery.fromFloatQuery(floatQuery(Integer.MAX_VALUE)));
  }

  public void testSeededKMaxValue() throws Exception {
    Query seed = new TermQuery(new Term("tag", "x"));
    expectAll(
        "SeededKnnVectorQuery k=MAX_VALUE",
        1,
        SeededKnnVectorQuery.fromFloatQuery(floatQuery(Integer.MAX_VALUE), seed));
  }

  public void testSeededFourSegmentsKMaxValue() throws Exception {
    Query seed = new TermQuery(new Term("tag", "x"));
    expectAll(
        "SeededKnnVectorQuery, 4 segments, k=MAX_VALUE",
        4,
        SeededKnnVectorQuery.fromFloatQuery(floatQuery(Integer.MAX_VALUE), seed));
  }
}
