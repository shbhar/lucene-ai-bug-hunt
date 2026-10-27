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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.StableTflSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * BM25Similarity and StableTflSimilarity accept every finite k3, but computeQueryTermWeight
 * evaluates ((k3 + 1) * qtf) / (k3 + qtf) in float, so the numerator overflows to +Infinity once
 * k3 > Float.MAX_VALUE / qtf (for qtf = 2: k3 > about 1.7e38) although the true value is below qtf.
 * BooleanQuery's duplicate-clause rewrite then builds a BoostQuery with an infinite boost, which
 * throws IllegalArgumentException, so any query with a repeated term fails.
 */
public class TestK3QueryWeightOverflow extends LuceneTestCase {

  private static final float K3 = Float.MAX_VALUE; // finite: accepted by both constructors

  public void testBM25QueryTermWeightIsFinite() {
    BM25Similarity sim = new BM25Similarity(1.2f, 0.75f, true, K3);
    float w = sim.computeQueryTermWeight(2);
    // ((k3 + 1) * 2) / (k3 + 2) is in [1, 2] for every k3 >= 0
    assertTrue("computeQueryTermWeight(2) = " + w, w >= 1f && w <= 2f);
  }

  public void testStableTflQueryTermWeightIsFinite() {
    StableTflSimilarity sim = new StableTflSimilarity(1.2f, 0.917f, K3);
    float w = sim.computeQueryTermWeight(2);
    assertTrue("computeQueryTermWeight(2) = " + w, w >= 1f && w <= 2f);
  }

  public void testDuplicateTermQuerySearches() throws IOException {
    for (Similarity sim :
        new Similarity[] {
          new BM25Similarity(1.2f, 0.75f, true, K3), new StableTflSimilarity(1.2f, 0.917f, K3)
        }) {
      try (Directory dir = newDirectory()) {
        IndexWriterConfig iwc = new IndexWriterConfig();
        iwc.setSimilarity(sim);
        try (IndexWriter w = new IndexWriter(dir, iwc)) {
          Document doc = new Document();
          doc.add(new StringField("f", "a", Store.NO));
          w.addDocument(doc);
        }
        try (IndexReader reader = DirectoryReader.open(dir)) {
          IndexSearcher searcher = new IndexSearcher(reader);
          searcher.setSimilarity(sim);
          Query q =
              new BooleanQuery.Builder()
                  .add(new TermQuery(new Term("f", "a")), BooleanClause.Occur.SHOULD)
                  .add(new TermQuery(new Term("f", "a")), BooleanClause.Occur.SHOULD)
                  .build();
          TopDocs td;
          try {
            td = searcher.search(q, 10);
          } catch (IllegalArgumentException e) {
            throw new AssertionError(sim + ": query 'a a' threw " + e, e);
          }
          assertEquals(sim.toString(), 1, td.totalHits.value());
        }
      }
    }
  }
}
