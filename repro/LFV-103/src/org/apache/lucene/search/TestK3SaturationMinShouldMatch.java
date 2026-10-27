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

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.StableTflSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * BM25Similarity(k1, b, discountOverlaps, k3) documents: "When duplicate terms appear in a query,
 * their boost is computed as ((k3 + 1) * qtf) / (k3 + qtf) instead of linear summing." The
 * saturation is applied by BooleanQuery#rewrite when it merges duplicate clauses, but duplicate
 * SHOULD clauses are only merged when minimumNumberShouldMatch <= 1. With msm >= 2 the duplicate
 * term is summed linearly, so the same text scores differently depending on msm, and
 * non-monotonically: msm=2 scores a document higher than both msm=1 and msm=3.
 *
 * <p>Public API only (QueryBuilder, BM25Similarity, IndexSearcher).
 */
public class TestK3SaturationMinShouldMatch extends LuceneTestCase {

  private static final float K3 = 8f;

  private static float score(IndexSearcher s, Query q, int doc) throws IOException {
    Explanation e = s.explain(q, doc);
    assertTrue("doc " + doc + " must match " + q, e.isMatch());
    TopDocs td = s.search(q, 10);
    for (ScoreDoc sd : td.scoreDocs) {
      if (sd.doc == doc) {
        return sd.score;
      }
    }
    throw new AssertionError("doc " + doc + " not in top hits of " + q);
  }

  public void testDuplicateShouldTermSaturatedForEveryMinShouldMatch() throws IOException {
    check(new BM25Similarity(1.2f, 0.75f, true, K3));
  }

  public void testStableTflDuplicateShouldTermSaturatedForEveryMinShouldMatch() throws IOException {
    check(new StableTflSimilarity(StableTflSimilarity.DEFAULT_K1, StableTflSimilarity.DEFAULT_C, K3));
  }

  private void check(Similarity sim) throws IOException {
    try (Directory dir = newDirectory()) {
      Analyzer analyzer = new StandardAnalyzer();
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer).setSimilarity(sim);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        String[] bodies = {"a b", "a", "b c", "c d", "a c", "d"};
        for (int i = 0; i < bodies.length; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
          doc.add(new TextField("body", bodies[i], Field.Store.NO));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(sim);
        searcher.setQueryCache(null);
        final int doc = 0; // "a b"

        QueryBuilder qb = new QueryBuilder(analyzer);
        // "a a b": the term "a" appears twice in the query (qtf = 2)
        Query msm1 = qb.createMinShouldMatchQuery("body", "a a b", 0.5f); // (int) 1.5 = 1
        Query msm2 = qb.createMinShouldMatchQuery("body", "a a b", 0.7f); // (int) 2.1 = 2
        Query msm3 = qb.createMinShouldMatchQuery("body", "a a b", 1f); // all MUST
        assertEquals(1, ((BooleanQuery) msm1).getMinimumNumberShouldMatch());
        assertEquals(2, ((BooleanQuery) msm2).getMinimumNumberShouldMatch());

        float sa = score(searcher, new TermQuery(new Term("body", "a")), doc);
        float sb = score(searcher, new TermQuery(new Term("body", "b")), doc);
        float w2 = sim.computeQueryTermWeight(2); // (8+1)*2/(8+2) = 1.8
        float expected = w2 * sa + sb;

        float s1 = score(searcher, msm1, doc);
        float s2 = score(searcher, msm2, doc);
        float s3 = score(searcher, msm3, doc);
        String detail =
            "\n  sa="
                + sa
                + " sb="
                + sb
                + " w(2)="
                + w2
                + " expected w(2)*sa+sb="
                + expected
                + " linear 2*sa+sb="
                + (2 * sa + sb)
                + "\n  msm=1 score="
                + s1
                + " rewritten="
                + searcher.rewrite(msm1)
                + "\n  msm=2 score="
                + s2
                + " rewritten="
                + searcher.rewrite(msm2)
                + "\n  msm=3 score="
                + s3
                + " rewritten="
                + searcher.rewrite(msm3);
        // msm=1 and msm=3 apply the documented saturation
        assertEquals("msm=1" + detail, expected, s1, 1e-4f * expected);
        assertEquals("msm=3" + detail, expected, s3, 1e-4f * expected);
        // msm=2 must apply it too (the doc matches all three clauses in every variant)
        assertEquals(
            "duplicate query term must be saturated for msm=2 as well" + detail,
            expected,
            s2,
            1e-4f * expected);
      }
    }
  }
}
