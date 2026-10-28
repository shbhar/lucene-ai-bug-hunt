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
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * LMDirichletSimilarity accepts mu = 0 ("must be a non-negative finite value"), and upstream's
 * TestLMDirichletSimilarity uses 0 as its "minimum value". For every mu > 0, down to
 * Float.MIN_VALUE, a document's score is the Dirichlet formula log(1 + tf/(mu*P)) + log(mu/(dl+mu))
 * = log((tf + mu*P) / ((dl + mu) * P)), whose value at mu = 0 is log(tf / (dl * P)). At mu = 0 the
 * code evaluates the two logarithms separately, +Infinity + -Infinity = NaN, and the "score > 0"
 * clamp turns NaN into 0, so every document scores 0 and the ranking is lost.
 */
public class TestLMDirichletMuZero extends LuceneTestCase {

  // doc 0: tf(a)=1, dl=4; doc 1: tf=2, dl=3; doc 2: tf=1, dl=1 (the best match); docs 3-6 have no 'a'
  private static final String[] DOCS = {
    "a b c d", "a a b", "a", "b c d e f", "c d e f g", "d e f g h", "e f g h i"
  };

  private static TopDocs search(Directory dir, Similarity sim) throws Exception {
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setSimilarity(sim);
      TopDocs td = searcher.search(new TermQuery(new Term("body", "a")), 10);
      if (VERBOSE || sim instanceof LMDirichletSimilarity lm && lm.getMu() == 0f) {
        System.out.println(
            "explain(doc 2) with " + sim + ":\n" + searcher.explain(new TermQuery(new Term("body", "a")), 2));
      }
      return td;
    }
  }

  private static Directory index(Similarity sim) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setSimilarity(sim);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (String text : DOCS) {
        Document doc = new Document();
        doc.add(new TextField("body", text, Field.Store.NO));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  private static float scoreOf(TopDocs td, int doc) {
    for (ScoreDoc sd : td.scoreDocs) {
      if (sd.doc == doc) {
        return sd.score;
      }
    }
    throw new AssertionError("doc " + doc + " not a hit");
  }

  /** mu = 0 must score like its mu -> 0 limit (here: like mu = Float.MIN_VALUE). */
  public void testMuZeroScoresLikeTheLimit() throws Exception {
    Similarity zero = new LMDirichletSimilarity(0f);
    Similarity tiny = new LMDirichletSimilarity(Float.MIN_VALUE);
    try (Directory dir = index(zero)) { // norms do not depend on mu
      TopDocs atZero = search(dir, zero);
      TopDocs atTiny = search(dir, tiny);
      assertEquals(3, atZero.scoreDocs.length);
      // closed-form limit log(tf / (dl * P)), P = (ttf + 1) / (sumTotalTermFreq + 1) (default
      // collection model); ttf(a) = 4, 4 + 3 + 1 + 4 * 5 = 28 tokens
      double p = (4 + 1d) / (28 + 1d);
      int[] tf = {1, 2, 1};
      int[] dl = {4, 3, 1};
      for (int doc = 0; doc < 3; doc++) {
        double limit = Math.max(0d, Math.log(tf[doc] / (dl[doc] * p)));
        float tinyScore = scoreOf(atTiny, doc);
        assertEquals("mu=Float.MIN_VALUE, doc " + doc, limit, tinyScore, 1e-5);
        assertEquals(
            "mu=0 must give the mu->0 limit, like mu=Float.MIN_VALUE does; doc " + doc,
            tinyScore,
            scoreOf(atZero, doc),
            1e-5);
      }
    }
  }

  /** With mu = 0 the best match must still rank first. */
  public void testMuZeroKeepsTheRanking() throws Exception {
    Similarity zero = new LMDirichletSimilarity(0f);
    try (Directory dir = index(zero)) {
      TopDocs atTiny = search(dir, new LMDirichletSimilarity(Float.MIN_VALUE));
      TopDocs atZero = search(dir, zero);
      assertEquals("mu=Float.MIN_VALUE ranks doc 2 first", 2, atTiny.scoreDocs[0].doc);
      assertTrue(
          "mu=0: the best match (doc 2, tf/dl = 1) must score above 0, got "
              + atZero.scoreDocs[0].score,
          atZero.scoreDocs[0].score > 0f);
      assertEquals("mu=0 must rank the best match (doc 2) first", 2, atZero.scoreDocs[0].doc);
    }
  }
}
