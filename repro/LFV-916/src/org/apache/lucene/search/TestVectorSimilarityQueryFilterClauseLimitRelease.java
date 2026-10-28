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
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Release variant (10.5.x and older constructors: traversalSimilarity, resultSimilarity, filter).
 * Float/ByteVectorSimilarityQuery run their filter (rewritten and weighted in createWeight), but
 * visit() reports the query as a leaf and never reaches the filter, so IndexSearcher's cumulative
 * clause limit does not count the filter's clauses. maxClauseCount is the default 1024; each query
 * below holds 1200 term clauses in total.
 */
public class TestVectorSimilarityQueryFilterClauseLimitRelease extends LuceneTestCase {

  private static Query disjunction(String prefix, int n) {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (int i = 0; i < n; i++) {
      b.add(new TermQuery(new Term("f", prefix + i)), Occur.SHOULD);
    }
    return b.build();
  }

  private interface Body {
    void run(IndexSearcher searcher) throws Exception;
  }

  private void withSearcher(Body body) throws Exception {
    assertEquals(1024, IndexSearcher.getMaxClauseCount());
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 20; i++) {
        Document doc = new Document();
        doc.add(new StringField("f", "a" + i, Field.Store.NO));
        doc.add(new StringField("f", "b" + i, Field.Store.NO));
        doc.add(new KnnFloatVectorField("v", new float[] {1, i}, VectorSimilarityFunction.EUCLIDEAN));
        doc.add(new KnnByteVectorField("bv", new byte[] {1, (byte) i}, VectorSimilarityFunction.EUCLIDEAN));
        w.addDocument(doc);
      }
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        // control: the same clauses with the filter as a FILTER clause of a BooleanQuery
        Query control =
            new BooleanQuery.Builder()
                .add(disjunction("a", 600), Occur.MUST)
                .add(disjunction("b", 600), Occur.FILTER)
                .add(new FloatVectorSimilarityQuery("v", new float[] {1, 0}, 0f, 0f, null), Occur.MUST)
                .build();
        expectThrows(IndexSearcher.TooManyNestedClauses.class, () -> searcher.search(control, 10));
        body.run(searcher);
      }
    }
  }

  public void testFloatVectorSimilarityFilterClausesCountTowardsClauseLimit() throws Exception {
    withSearcher(
        searcher -> {
          Query query =
              new BooleanQuery.Builder()
                  .add(disjunction("a", 600), Occur.MUST)
                  .add(
                      new FloatVectorSimilarityQuery(
                          "v", new float[] {1, 0}, 0f, 0f, disjunction("b", 600)),
                      Occur.MUST)
                  .build();
          expectThrows(IndexSearcher.TooManyNestedClauses.class, () -> searcher.search(query, 10));
        });
  }

  public void testByteVectorSimilarityFilterClausesCountTowardsClauseLimit() throws Exception {
    withSearcher(
        searcher -> {
          Query query =
              new BooleanQuery.Builder()
                  .add(disjunction("a", 600), Occur.MUST)
                  .add(
                      new ByteVectorSimilarityQuery(
                          "bv", new byte[] {1, 0}, 0f, 0f, disjunction("b", 600)),
                      Occur.MUST)
                  .build();
          expectThrows(IndexSearcher.TooManyNestedClauses.class, () -> searcher.search(query, 10));
        });
  }
}
