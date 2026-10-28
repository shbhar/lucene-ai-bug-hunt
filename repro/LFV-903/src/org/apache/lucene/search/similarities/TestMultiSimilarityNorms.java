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
package org.apache.lucene.search.similarities;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * MultiSimilarity "will sum the scores of the provided sims" (CombSUM). Every sub-similarity's
 * SimScorer expects the norm "as computed by Similarity#computeNorm" at index time, but
 * MultiSimilarity.computeNorm writes only sims[0]'s norm and every sub-scorer receives it. When the
 * sub-similarities compute different norms, here through the public discountOverlaps constructor
 * parameter and a document with a synonym (position increment 0), the combined score is neither the
 * sum of the sub-similarities' scores nor independent of the order of the sub-similarities.
 */
public class TestMultiSimilarityNorms extends LuceneTestCase {

  // doc 0: "a" ; doc 1: "a" plus synonym "x" at the same position (one overlap) ; doc 2: "a b" plus
  // two synonyms ; doc 3: "b c" (no 'a', keeps idf finite and the field statistics non-trivial)
  private static Token[][] docs() {
    return new Token[][] {
      {tok("a", 1)},
      {tok("a", 1), tok("x", 0)},
      {tok("a", 1), tok("y", 0), tok("b", 1), tok("z", 0)},
      {tok("b", 1), tok("c", 1)},
    };
  }

  private static Token tok(String term, int posInc) {
    Token t = new Token(term, 0, term.length());
    t.setPositionIncrement(posInc);
    return t;
  }

  /** Index the documents with {@code sim} (writer and searcher) and score body:a, per doc. */
  private static float[] scores(Similarity sim) throws Exception {
    Token[][] docs = docs();
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setSimilarity(sim);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (Token[] toks : docs) {
          Document doc = new Document();
          doc.add(new TextField("body", new CannedTokenStream(toks)));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(sim);
        float[] out = new float[docs.length];
        for (ScoreDoc sd : searcher.search(new TermQuery(new Term("body", "a")), 10).scoreDocs) {
          out[sd.doc] = sd.score;
        }
        return out;
      }
    }
  }

  private static void assertCombSum(Similarity a, Similarity b) throws Exception {
    float[] sa = scores(a);
    float[] sb = scores(b);
    float[] ab = scores(new MultiSimilarity(new Similarity[] {a, b}));
    float[] ba = scores(new MultiSimilarity(new Similarity[] {b, a}));
    for (int doc = 0; doc < 3; doc++) {
      double sum = (double) sa[doc] + sb[doc];
      String msg =
          "A="
              + a
              + " (discountOverlaps="
              + a.getDiscountOverlaps()
              + "), B="
              + b
              + " (discountOverlaps="
              + b.getDiscountOverlaps()
              + "), doc "
              + doc
              + ": score(A)="
              + sa[doc]
              + " score(B)="
              + sb[doc];
      assertEquals(msg + ", MultiSimilarity[A,B] vs MultiSimilarity[B,A]", ab[doc], ba[doc], 0f);
      assertEquals(msg + ", MultiSimilarity[A,B] vs score(A) + score(B)", sum, ab[doc], 1e-6);
    }
  }

  /** BM25 with and without discountOverlaps. */
  public void testBM25DiscountOverlapsMix() throws Exception {
    assertCombSum(new BM25Similarity(1.2f, 0.75f, true), new BM25Similarity(1.2f, 0.75f, false));
  }

  /** ClassicSimilarity with and without discountOverlaps. */
  public void testClassicDiscountOverlapsMix() throws Exception {
    assertCombSum(new ClassicSimilarity(true), new ClassicSimilarity(false));
  }

  /** BM25 (discountOverlaps=true) with LM Dirichlet (discountOverlaps=false). */
  public void testBM25AndLMDirichletDiscountOverlapsMix() throws Exception {
    assertCombSum(
        new BM25Similarity(),
        new LMDirichletSimilarity(new LMSimilarity.DefaultCollectionModel(), false, 2000f));
  }

  /** Control: sub-similarities that compute the same norm satisfy CombSUM. */
  public void testControlSameNorms() throws Exception {
    assertCombSum(new BM25Similarity(), new ClassicSimilarity());
    assertCombSum(new BM25Similarity(1.2f, 0.75f, false), new ClassicSimilarity(false));
  }
}
