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

/** Parent-join kNN with k larger than the index: 2 parents, 2 child vectors each. Public API. */
public class TestDiversifyingKnnLargeK extends LuceneTestCase {

  private int parentHits(int k) throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        float[][][] kids = {{{1f, 0f}, {0f, 1f}}, {{1f, 1f}, {2f, 1f}}};
        for (int p = 0; p < kids.length; p++) {
          List<Document> block = new ArrayList<>();
          for (float[] v : kids[p]) {
            Document c = new Document();
            c.add(new KnnFloatVectorField("vec", v, VectorSimilarityFunction.EUCLIDEAN));
            block.add(c);
          }
          Document parent = new Document();
          parent.add(new StringField("docType", "parent", Field.Store.NO));
          block.add(parent);
          w.addDocuments(block);
        }
        w.commit();
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        IndexSearcher s = new IndexSearcher(r);
        BitSetProducer parents =
            new QueryBitSetProducer(new TermQuery(new Term("docType", "parent")));
        Query q =
            new DiversifyingChildrenFloatKnnVectorQuery(
                "vec", new float[] {1f, 0f}, null, k, parents);
        return s.search(q, 10).scoreDocs.length;
      }
    }
  }

  public void testControlK1000() throws Exception {
    assertEquals(2, parentHits(1000));
  }

  public void testMaxK() throws Exception {
    try {
      assertEquals(2, parentHits(Integer.MAX_VALUE));
    } catch (IllegalArgumentException e) {
      throw new AssertionError("DiversifyingChildrenFloatKnnVectorQuery k=MAX_VALUE threw", e);
    }
  }

  public void testK2pow28() throws Exception {
    try {
      assertEquals(2, parentHits(1 << 28));
    } catch (OutOfMemoryError e) {
      throw new AssertionError("DiversifyingChildrenFloatKnnVectorQuery k=2^28: OOM", e);
    } catch (RuntimeException e) {
      throw new AssertionError("DiversifyingChildrenFloatKnnVectorQuery k=2^28 threw", e);
    }
  }
}
