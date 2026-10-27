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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Two fuzzy queries whose expansions share the term "aaaa" are rewritten (blended frequencies)
 * into TermQuery(aaaa) clauses with the same artificial docFreq but different totalTermFreq.
 * BooleanQuery's rewrite merges equal SHOULD clauses; TermQuery equality (since #12354) compares
 * the TermStates, but TermStates#equals only compares docFreq, so the two clauses are merged and
 * the second clause's statistics are lost. A disjunction of two SHOULD clauses must score the
 * sum of the clause scores.
 */
public class TestBlendedTermStatesMerge extends LuceneTestCase {

  private static final String[] DOCS = {
    "aaaa zz", "aaaa yy", "aaaa xx", "aaaab", "baaaa baaaa baaaa baaaa baaaa", "zz yy xx ww vv"
  };

  private void check(Similarity sim) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setSimilarity(sim))) {
        for (String s : DOCS) {
          Document d = new Document();
          d.add(new TextField("t", s, Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(r);
        searcher.setSimilarity(sim);
        // expansions {aaaa, aaaab} and {aaaa, baaaa}: blended docFreq = max = 3 for both,
        // blended totalTermFreq = 3 + 1 and 3 + 5
        Query f1 = new FuzzyQuery(new Term("t", "aaaab"), 1);
        Query f2 = new FuzzyQuery(new Term("t", "baaaa"), 1);
        Query both =
            new BooleanQuery.Builder()
                .add(f1, BooleanClause.Occur.SHOULD)
                .add(f2, BooleanClause.Occur.SHOULD)
                .build();
        float[] sBoth = scores(searcher, both, r.maxDoc());
        float[] s1 = scores(searcher, f1, r.maxDoc());
        float[] s2 = scores(searcher, f2, r.maxDoc());
        for (int doc = 0; doc < r.maxDoc(); doc++) {
          float expected = s1[doc] + s2[doc];
          assertEquals(
              sim + " doc " + doc + " (rewritten: " + searcher.rewrite(both) + ")",
              expected,
              sBoth[doc],
              1e-5f * Math.max(1e-3f, Math.abs(expected)));
        }
      }
    }
  }

  private static float[] scores(IndexSearcher searcher, Query q, int maxDoc) throws IOException {
    float[] out = new float[maxDoc];
    for (ScoreDoc sd : searcher.search(q, maxDoc).scoreDocs) {
      out[sd.doc] = sd.score;
    }
    return out;
  }

  /**
   * The same two clauses in the other order form an equal BooleanQuery (BooleanQuery#equals is
   * order-insensitive for SHOULD clauses), so both must score every document identically.
   */
  public void testEqualQueriesScoreDifferently() throws IOException {
    Similarity sim = new LMDirichletSimilarity();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setSimilarity(sim))) {
        for (String s : DOCS) {
          Document d = new Document();
          d.add(new TextField("t", s, Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(r);
        searcher.setSimilarity(sim);
        Query f1 = new FuzzyQuery(new Term("t", "aaaab"), 1);
        Query f2 = new FuzzyQuery(new Term("t", "baaaa"), 1);
        Query q12 =
            new BooleanQuery.Builder()
                .add(f1, BooleanClause.Occur.SHOULD)
                .add(f2, BooleanClause.Occur.SHOULD)
                .build();
        Query q21 =
            new BooleanQuery.Builder()
                .add(f2, BooleanClause.Occur.SHOULD)
                .add(f1, BooleanClause.Occur.SHOULD)
                .build();
        assertEquals(q12, q21); // precondition: the two queries are equal
        float[] s12 = scores(searcher, q12, r.maxDoc());
        float[] s21 = scores(searcher, q21, r.maxDoc());
        for (int doc = 0; doc < r.maxDoc(); doc++) {
          assertEquals("doc " + doc, s12[doc], s21[doc], 0f);
        }
      }
    }
  }

  /** LM Dirichlet uses totalTermFreq (collection probability): the merge changes scores. */
  public void testLMDirichletDisjunctionOfFuzzyQueries() throws IOException {
    check(new LMDirichletSimilarity());
  }

  /** Control: BM25 does not use totalTermFreq, so the merge is harmless there. */
  public void testBM25Control() throws IOException {
    check(new BM25Similarity());
  }
}
