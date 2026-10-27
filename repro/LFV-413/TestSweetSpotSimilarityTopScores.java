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

import java.io.IOException;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.FieldStats;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermStats;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.search.CheckHits;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.SmallFloat;

/**
 * SweetSpotSimilarity with a length plateau (setLengthNormFactors with min >= 2) must keep the
 * Similarity#computeNorm contract, "greater unsigned norms must produce scores that are lower or
 * equal", because top-k search skips postings blocks based on it.
 *
 * <p>Index (one segment, every document contains "t"): docs 0-1999 are "t" plus filler words, so
 * that their length is the plateau minimum (score 1.0); docs 2000-2999 are "t t" (2 terms, below
 * the plateau), except doc 2150, "t t" plus filler words (plateau length), the best hit. Doc 2150
 * sits in the block of 256 postings 2048-2303, next to short documents.
 */
public class TestSweetSpotSimilarityTopScores extends LuceneTestCase {

  private static final int NUM_DOCS = 3000;
  private static final int FIRST_SHORT_DOC = 2000; // after the 1,000 hits counted exactly
  private static final int BEST_DOC = 2150;

  private static SweetSpotSimilarity plateau(int min, int max) {
    SweetSpotSimilarity sim = new SweetSpotSimilarity();
    sim.setLengthNormFactors(min, max, 0.5f);
    return sim;
  }

  /** {@code ts} occurrences of "t", then filler words up to {@code length} terms. */
  private static String text(int ts, int length) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
      sb.append(i == 0 ? "" : " ").append(i < ts ? "t" : "w");
    }
    return sb.toString();
  }

  private static Directory index(Similarity sim, int plateauLength) throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig(new StandardAnalyzer())
            .setSimilarity(sim)
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            .setRAMBufferSizeMB(256);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < NUM_DOCS; i++) {
        String value;
        if (i < FIRST_SHORT_DOC) {
          value = text(1, plateauLength); // "t w ...": on the plateau
        } else if (i == BEST_DOC) {
          value = text(2, plateauLength); // "t t w ...": on the plateau, the best hit
        } else {
          value = text(2, 2); // "t t": below the plateau
        }
        Document doc = new Document();
        doc.add(new TextField("f", value, Field.Store.NO));
        w.addDocument(doc);
      }
    }
    return dir;
  }

  private static IndexSearcher searcher(DirectoryReader reader, Similarity sim) {
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(sim);
    searcher.setQueryCache(null);
    return searcher;
  }

  private static String docs(TopDocs topDocs) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < topDocs.scoreDocs.length; i++) {
      sb.append(i == 0 ? "" : ", ")
          .append("doc ")
          .append(topDocs.scoreDocs[i].doc)
          .append(" (")
          .append(topDocs.scoreDocs[i].score)
          .append(")");
      if (i == 2) {
        sb.append(", ...");
        break;
      }
    }
    return sb.append("]").toString();
  }

  /** IndexSearcher#search(q, 10) must return the same top 10 as a search that counts every hit. */
  private static void assertTop10MatchesExhaustiveSearch(
      SweetSpotSimilarity sim, int plateauLength, int expectedTopDoc) throws IOException {
    try (Directory dir = index(sim, plateauLength);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(1, reader.leaves().size());
      IndexSearcher searcher = searcher(reader, sim);
      Query query = new TermQuery(new Term("f", "t"));
      // counts every hit, so no block is skipped
      TopDocs exhaustive =
          searcher.search(query, new TopScoreDocCollectorManager(10, Integer.MAX_VALUE));
      assertEquals(expectedTopDoc, exhaustive.scoreDocs[0].doc);
      // the default: counts 1,000 hits exactly, then skips blocks that cannot compete
      TopDocs top10 = searcher.search(query, 10);
      String message =
          sim
              + ": search(q, 10) returned "
              + docs(top10)
              + ", exhaustive search "
              + docs(exhaustive);
      assertEquals(message, exhaustive.scoreDocs.length, top10.scoreDocs.length);
      for (int i = 0; i < exhaustive.scoreDocs.length; i++) {
        assertEquals(message, exhaustive.scoreDocs[i].doc, top10.scoreDocs[i].doc);
        assertEquals(message, exhaustive.scoreDocs[i].score, top10.scoreDocs[i].score, 0f);
      }
    }
  }

  /** Similarity#computeNorm: for the same freq, a greater norm must not score higher. */
  public void testGreaterNormNeverScoresHigher() {
    SweetSpotSimilarity sim = plateau(10, 20);
    Similarity.SimScorer scorer =
        sim.scorer(
            1f,
            new FieldStats("f", 1000, 1000, 10000, 10000),
            new TermStats(new BytesRef("t"), 1000, 1000)); // idf = 1
    int maxNorm = SmallFloat.intToByte4(1000) & 0xFF; // the norms of fields of 1 to 1,000 terms
    for (int norm = 2; norm <= maxNorm; norm++) {
      float greater = scorer.score(1f, norm);
      float smaller = scorer.score(1f, norm - 1);
      assertTrue(
          sim
              + ": freq 1, norm "
              + norm
              + " scores "
              + greater
              + " > norm "
              + (norm - 1)
              + " scores "
              + smaller,
          greater <= smaller);
    }
  }

  /** Scorer#getMaxScore must bound the scores of the documents it covers. */
  public void testMaxScoreIsUpperBound() throws IOException {
    SweetSpotSimilarity sim = plateau(10, 20);
    try (Directory dir = index(sim, 10);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = searcher(reader, sim);
      Query query = searcher.rewrite(new TermQuery(new Term("f", "t")));
      Weight weight = searcher.createWeight(query, ScoreMode.TOP_SCORES, 1f);
      Scorer scorer = weight.scorer(reader.leaves().get(0));
      scorer.advanceShallow(BEST_DOC);
      float maxScore = scorer.getMaxScore(BEST_DOC); // covers the block of 256 postings of doc 2150
      assertEquals(BEST_DOC, scorer.iterator().advance(BEST_DOC));
      float score = scorer.score();
      assertTrue(
          sim
              + ": getMaxScore("
              + BEST_DOC
              + ") = "
              + maxScore
              + " but doc "
              + BEST_DOC
              + " scores "
              + score,
          score <= maxScore);
    }
  }

  public void testTop10MatchesExhaustiveSearch() throws IOException {
    assertTop10MatchesExhaustiveSearch(plateau(10, 20), 10, BEST_DOC);
  }

  /** The plateau of the Solr SweetSpotSimilarityFactory javadoc example. */
  public void testTop10MatchesExhaustiveSearchSmallPlateau() throws IOException {
    assertTop10MatchesExhaustiveSearch(plateau(3, 5), 3, BEST_DOC);
  }

  /** Lucene's own check that pruned and exhaustive top-k search agree. */
  public void testCheckTopScores() throws IOException {
    SweetSpotSimilarity sim = plateau(10, 20);
    try (Directory dir = index(sim, 10);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      CheckHits.checkTopScores(random(), new TermQuery(new Term("f", "t")), searcher(reader, sim));
    }
  }

  /**
   * Control: the default plateau (min = max = 1) gives 1/sqrt(length), which keeps the contract;
   * here the short docs are the best hits.
   */
  public void testDefaultPlateauControl() throws IOException {
    assertTop10MatchesExhaustiveSearch(plateau(1, 1), 10, FIRST_SHORT_DOC);
  }
}
