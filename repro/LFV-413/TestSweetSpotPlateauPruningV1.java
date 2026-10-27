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
package org.apache.lucene.misc;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.SmallFloat;

/**
 * Independent verifier repro: SweetSpotSimilarity with a length plateau (min >= 2) is not
 * monotone in the encoded norm, which Similarity#computeNorm requires and which impact-based
 * (TOP_SCORES) pruning relies on.
 *
 * <p>Index layout (one segment, every doc contains "t", so postings ordinal == doc id):
 *
 * <ul>
 *   <li>all docs except two: "t" once in a 10-token field (score 1.0 with the plateau [10, 20]);
 *   <li>doc SHORT: "t t" (2 tokens): freq 2, length 2 (below the plateau, lengthNorm = 1/3);
 *   <li>doc BEST (= SHORT + 1, same postings block): "t t" + 8 fillers: freq 2, length 10 (on the
 *       plateau, lengthNorm = 1). This is the single best hit.
 * </ul>
 *
 * Uses only public API.
 */
public class TestSweetSpotPlateauPruningV1 extends LuceneTestCase {

  private static final int NUM_DOCS = 3000;
  private static final int SHORT = 2000; // well past the default 1000 total-hits threshold
  private static final int BEST = SHORT + 1;

  private static SweetSpotSimilarity plateauSim(int min, int max) {
    SweetSpotSimilarity sim = new SweetSpotSimilarity();
    sim.setLengthNormFactors(min, max, 0.5f);
    return sim;
  }

  private static String doc(int i) {
    if (i == SHORT) {
      return "t t";
    } else if (i == BEST) {
      return "t t a b c d e f g h";
    } else {
      return "t a b c d e f g h i";
    }
  }

  private Directory buildIndex(Similarity sim) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
    iwc.setSimilarity(sim);
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    iwc.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    iwc.setRAMBufferSizeMB(256);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < NUM_DOCS; i++) {
        Document d = new Document();
        d.add(new Field("f", doc(i), TextField.TYPE_NOT_STORED));
        w.addDocument(d);
      }
      w.commit();
    }
    return dir;
  }

  /**
   * Similarity#computeNorm contract, checked end to end: two docs with the same freq of "t", one
   * with a 2-token field (norm byte 2) and one with a 10-token field (norm byte 10). The greater
   * norm must not score higher.
   */
  public void testNormContract() throws Exception {
    SweetSpotSimilarity sim = plateauSim(10, 20);
    long n2 = SmallFloat.intToByte4(2) & 0xFF;
    long n10 = SmallFloat.intToByte4(10) & 0xFF;
    assertTrue("precondition: n10 > n2 unsigned", Long.compareUnsigned(n10, n2) > 0);
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
      iwc.setSimilarity(sim);
      iwc.setCodec(TestUtil.getDefaultCodec());
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (String text : new String[] {"t a", "t a b c d e f g h i"}) {
          Document d = new Document();
          d.add(new Field("f", text, TextField.TYPE_NOT_STORED));
          w.addDocument(d);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        LeafReaderContext ctx = reader.leaves().get(0);
        org.apache.lucene.index.NumericDocValues norms = ctx.reader().getNormValues("f");
        assertTrue(norms.advanceExact(0));
        assertEquals(n2, norms.longValue() & 0xFF);
        assertTrue(norms.advanceExact(1));
        assertEquals(n10, norms.longValue() & 0xFF);
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(sim);
        TopDocs td =
            searcher.search(
                new TermQuery(new Term("f", "t")),
                new TopScoreDocCollectorManager(10, Integer.MAX_VALUE));
        float s2 = Float.NaN, s10 = Float.NaN;
        for (org.apache.lucene.search.ScoreDoc sd : td.scoreDocs) {
          if (sd.doc == 0) s2 = sd.score;
          if (sd.doc == 1) s10 = sd.score;
        }
        assertTrue(
            "Similarity#computeNorm contract: freq 1, norm "
                + n10
                + " scored "
                + s10
                + " > norm "
                + n2
                + " scored "
                + s2,
            s10 <= s2);
      }
    }
  }

  /** Scorer#getMaxScore must be an upper bound of the score of every doc in the range. */
  public void testMaxScoreIsUpperBound() throws Exception {
    SweetSpotSimilarity sim = plateauSim(10, 20);
    try (Directory dir = buildIndex(sim);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(1, reader.leaves().size());
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setSimilarity(sim);
      Query q = searcher.rewrite(new TermQuery(new Term("f", "t")));
      Weight weight = searcher.createWeight(q, ScoreMode.TOP_SCORES, 1f);
      LeafReaderContext ctx = reader.leaves().get(0);
      Scorer scorer = weight.scorer(ctx);
      scorer.advanceShallow(BEST);
      float bound = scorer.getMaxScore(BEST);
      assertEquals(BEST, scorer.iterator().advance(BEST));
      float actual = scorer.score();
      assertTrue(
          "Scorer#getMaxScore(" + BEST + ") = " + bound + " but doc " + BEST + " scores " + actual,
          actual <= bound);
    }
  }

  /** IndexSearcher.search(q, 10) must return the same top hits as a complete evaluation. */
  public void testTopHitsMatchCompleteEvaluation() throws Exception {
    SweetSpotSimilarity sim = plateauSim(10, 20);
    try (Directory dir = buildIndex(sim);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(1, reader.leaves().size());
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setSimilarity(sim);
      Query q = new TermQuery(new Term("f", "t"));
      TopDocs complete = searcher.search(q, new TopScoreDocCollectorManager(10, Integer.MAX_VALUE));
      TopDocs pruned = searcher.search(q, 10);
      // sanity: the complete evaluation ranks BEST first
      assertEquals(BEST, complete.scoreDocs[0].doc);
      assertEquals(
          "top hit of search(q, 10): expected doc "
              + BEST
              + " (score "
              + complete.scoreDocs[0].score
              + "), got doc "
              + pruned.scoreDocs[0].doc
              + " (score "
              + pruned.scoreDocs[0].score
              + ")",
          complete.scoreDocs[0].doc,
          pruned.scoreDocs[0].doc);
    }
  }

  /** Control: the default plateau (min = max = 1) is monotone; same index layout, no mismatch. */
  public void testControlDefaultPlateau() throws Exception {
    SweetSpotSimilarity sim = plateauSim(1, 1);
    try (Directory dir = buildIndex(sim);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setSimilarity(sim);
      Query q = new TermQuery(new Term("f", "t"));
      TopDocs complete = searcher.search(q, new TopScoreDocCollectorManager(10, Integer.MAX_VALUE));
      TopDocs pruned = searcher.search(q, 10);
      assertEquals(complete.scoreDocs.length, pruned.scoreDocs.length);
      for (int i = 0; i < complete.scoreDocs.length; i++) {
        assertEquals(complete.scoreDocs[i].doc, pruned.scoreDocs[i].doc);
        assertEquals(complete.scoreDocs[i].score, pruned.scoreDocs[i].score, 0f);
      }
    }
  }
}
