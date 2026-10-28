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
package org.apache.lucene.search.join;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnByteVectorField;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/** Parent-join kNN with k = Integer.MAX_VALUE: byte vectors, and float vectors in 2 segments. */
public class TestDiversifyingKnnLargeKVariants extends LuceneTestCase {

  /** Each segment holds 2 parents with 2 child vectors each. */
  private static Directory index(int segments) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int s = 0; s < segments; s++) {
        int[][][] kids = {{{1, 0}, {0, 1}}, {{1, 1}, {2, 1}}};
        for (int[][] parentKids : kids) {
          List<Document> block = new ArrayList<>();
          for (int[] v : parentKids) {
            Document c = new Document();
            c.add(
                new KnnFloatVectorField(
                    "vec", new float[] {v[0], v[1]}, VectorSimilarityFunction.EUCLIDEAN));
            byte[] bv = {(byte) v[0], (byte) v[1]};
            c.add(new KnnByteVectorField("bvec", bv, VectorSimilarityFunction.EUCLIDEAN));
            block.add(c);
          }
          Document parent = new Document();
          parent.add(new StringField("docType", "parent", Field.Store.NO));
          block.add(parent);
          w.addDocuments(block);
        }
        w.commit();
      }
    }
    return dir;
  }

  private static int parentHits(int segments, Query q) throws Exception {
    try (Directory dir = index(segments);
        IndexReader r = DirectoryReader.open(dir)) {
      assertEquals(segments, r.leaves().size());
      return new IndexSearcher(r).search(q, 100).scoreDocs.length;
    }
  }

  private static BitSetProducer parents() {
    return new QueryBitSetProducer(new TermQuery(new Term("docType", "parent")));
  }

  private static void expectParents(String what, int expected, int segments, Query q)
      throws Exception {
    try {
      assertEquals(what, expected, parentHits(segments, q));
    } catch (IllegalArgumentException | OutOfMemoryError e) {
      throw new AssertionError(what + " threw " + e, e);
    }
  }

  public void testByteMaxK() throws Exception {
    expectParents(
        "DiversifyingChildrenByteKnnVectorQuery k=MAX_VALUE",
        2,
        1,
        new DiversifyingChildrenByteKnnVectorQuery(
            "bvec", new byte[] {1, 0}, null, Integer.MAX_VALUE, parents()));
  }

  public void testFloatTwoSegmentsMaxK() throws Exception {
    expectParents(
        "DiversifyingChildrenFloatKnnVectorQuery, 2 segments, k=MAX_VALUE",
        4,
        2,
        new DiversifyingChildrenFloatKnnVectorQuery(
            "vec", new float[] {1f, 0f}, null, Integer.MAX_VALUE, parents()));
  }
}
