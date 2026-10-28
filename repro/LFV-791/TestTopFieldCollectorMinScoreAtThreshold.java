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

import static org.apache.lucene.search.SortField.FIELD_SCORE;

import java.util.Arrays;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * A TopFieldCollector whose primary sort is descending relevance should start passing its bottom
 * score to {@link Scorable#setMinCompetitiveScore} once more than totalHitsThreshold hits were
 * counted, also when the hit that crosses the threshold is not competitive, as TopScoreDocCollector
 * does (LUCENE-8507).
 */
public class TestTopFieldCollectorMinScoreAtThreshold extends LuceneTestCase {

  private static class Score extends Scorable {
    float score;
    Float minCompetitiveScore = null;

    @Override
    public void setMinCompetitiveScore(float minCompetitiveScore) {
      this.minCompetitiveScore = minCompetitiveScore;
    }

    @Override
    public float score() {
      return score;
    }
  }

  /** Collector level: numHits=2, totalHitsThreshold=2, the third hit is not competitive. */
  public void testMinScoreSetOnNonCompetitiveThresholdCrossingHit() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w =
        new IndexWriter(dir, newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE));
    Document doc = new Document();
    w.addDocuments(Arrays.asList(doc, doc, doc));
    w.flush();
    w.addDocuments(Arrays.asList(doc, doc));
    w.flush();
    IndexReader reader = DirectoryReader.open(w);
    assertEquals(2, reader.leaves().size());
    w.close();

    // Control: TopScoreDocCollector sets the min competitive score on the non-competitive hit that
    // crosses the threshold.
    TopScoreDocCollector topScores = new TopScoreDocCollectorManager(2, 2).newCollector();
    Score scorer = new Score();
    LeafCollector leafCollector = topScores.getLeafCollector(reader.leaves().get(0));
    leafCollector.setScorer(scorer);
    scorer.score = 3;
    leafCollector.collect(0);
    scorer.score = 2;
    leafCollector.collect(1);
    assertNull(scorer.minCompetitiveScore);
    scorer.score = 1;
    leafCollector.collect(2);
    assertEquals(Float.valueOf(Math.nextUp(2f)), scorer.minCompetitiveScore);

    for (Sort sort :
        new Sort[] {
          Sort.RELEVANCE, new Sort(FIELD_SCORE, new SortField("foo", SortField.Type.LONG))
        }) {
      TopFieldCollector collector = new TopFieldCollectorManager(sort, 2, 2).newCollector();
      scorer = new Score();
      leafCollector = collector.getLeafCollector(reader.leaves().get(0));
      leafCollector.setScorer(scorer);
      scorer.score = 3;
      leafCollector.collect(0);
      scorer.score = 2;
      leafCollector.collect(1);
      // the threshold is not exceeded yet
      assertNull(scorer.minCompetitiveScore);

      // the third hit exceeds totalHitsThreshold and is not competitive
      scorer.score = 1;
      leafCollector.collect(2);
      assertEquals(
          sort + ": min competitive score after the threshold-crossing hit",
          Float.valueOf(2f),
          scorer.minCompetitiveScore);

      // the next segment starts with the shared min competitive score
      scorer = new Score();
      leafCollector = collector.getLeafCollector(reader.leaves().get(1));
      leafCollector.setScorer(scorer);
      assertEquals(
          sort + ": min competitive score on the next segment",
          Float.valueOf(2f),
          scorer.minCompetitiveScore);
    }

    reader.close();
    dir.close();
  }

  /**
   * Searcher level: 10,011 matches, the best 1,011 documents first. search(q, 10) stops scoring
   * after about 1,000 hits; the relevance-first sorted searches should too.
   */
  public void testRelevanceSortSkipsLikeScoreSort() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
    iwc.setCodec(TestUtil.getDefaultCodec());
    IndexWriter w = new IndexWriter(dir, iwc);
    final int numDocs = 10011;
    for (int i = 0; i < numDocs; i++) {
      Document d = new Document();
      d.add(new TextField("body", i <= 1010 ? "a b c" : "a b", Field.Store.NO));
      d.add(new NumericDocValuesField("id", i));
      w.addDocument(d);
    }
    w.forceMerge(1);
    w.close();
    IndexReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setQueryCache(null);

    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (String t : new String[] {"a", "b", "c"}) {
      b.add(new TermQuery(new Term("body", t)), BooleanClause.Occur.SHOULD);
    }
    Query q = b.build();
    assertEquals(numDocs, searcher.count(q));

    Sort scoreThenId = new Sort(FIELD_SCORE, new SortField("id", SortField.Type.LONG));
    TopDocs byScore = searcher.search(q, 10);
    TopDocs relevance = searcher.search(q, 10, Sort.RELEVANCE);
    TopDocs scoreId = searcher.search(q, 10, scoreThenId);
    TopDocs byScorePage2 = searcher.searchAfter(byScore.scoreDocs[9], q, 10);
    TopDocs scoreIdPage2 = searcher.searchAfter(scoreId.scoreDocs[9], q, 10, scoreThenId);

    for (TopDocs td : new TopDocs[] {relevance, scoreId}) {
      assertArrayEquals(docs(byScore), docs(td));
    }
    assertArrayEquals(docs(byScorePage2), docs(scoreIdPage2));

    String counts =
        "matches="
            + numDocs
            + "; search(q, 10): "
            + byScore.totalHits
            + "; search(q, 10, Sort.RELEVANCE): "
            + relevance.totalHits
            + "; search(q, 10, Sort(FIELD_SCORE, id)): "
            + scoreId.totalHits
            + "; page 2, searchAfter(q, 10): "
            + byScorePage2.totalHits
            + "; page 2, searchAfter(q, 10, Sort(FIELD_SCORE, id)): "
            + scoreIdPage2.totalHits;
    if (VERBOSE) {
      System.out.println(counts);
    }
    long limit = 2 * byScore.totalHits.value();
    assertTrue(counts, relevance.totalHits.value() <= limit);
    assertTrue(counts, scoreId.totalHits.value() <= limit);
    assertTrue(counts, scoreIdPage2.totalHits.value() <= 2 * byScorePage2.totalHits.value());

    reader.close();
    dir.close();
  }

  private static int[] docs(TopDocs td) {
    return Arrays.stream(td.scoreDocs).mapToInt(sd -> sd.doc).toArray();
  }
}
