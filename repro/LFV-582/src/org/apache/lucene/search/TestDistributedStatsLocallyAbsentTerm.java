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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.queries.spans.SpanOrQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndexSearcher#termStats and #fieldStats "can be overridden for example, to return a term's
 * [field's] statistics across a distributed collection". With such an override, a document on a
 * shard should score as it scores in the whole collection. TestShardSearching asserts exactly this
 * for TermQuery and PrefixQuery.
 *
 * <p>Queries that combine the statistics of several terms (SynonymQuery, MultiPhraseQuery,
 * SpanOrQuery, CombinedFieldQuery) only call termStats for terms whose docFreq on the local shard
 * is greater than 0. A query term that occurs only on other shards therefore never reaches the
 * override, its collection-wide statistics are left out, and the shard scores its documents
 * differently from the whole collection.
 *
 * <p>Two shards. Shard 0 holds doc A (body "quick fox", title "red"). Shard 1 holds B, C, D (body
 * "fast dog", "fast cat", "fast fast bird"; title "fox", "fox", "bird"). So body:fast and title:fox
 * exist only on shard 1. The whole collection is a MultiReader over both shards, as in
 * TestShardSearching.
 */
public class TestDistributedStatsLocallyAbsentTerm extends LuceneTestCase {

  /**
   * Searches one shard, scoring with statistics summed over all shards: the same arithmetic as
   * ShardSearchingTestBase.NodeState.ShardIndexSearcher (local values passed in, plus the other
   * shards' values).
   */
  static final class DistributedStatsSearcher extends IndexSearcher {
    private final IndexSearcher[] shards;
    private final int myShard;

    DistributedStatsSearcher(IndexSearcher[] shards, int myShard) {
      super(shards[myShard].getIndexReader());
      this.shards = shards;
      this.myShard = myShard;
    }

    @Override
    public TermStats termStats(Term term, int docFreq, long totalTermFreq) throws IOException {
      long df = 0;
      long ttf = 0;
      for (int i = 0; i < shards.length; i++) {
        if (i == myShard) {
          df += docFreq;
          ttf += totalTermFreq;
        } else {
          TermStates ts = TermStates.build(shards[i], term, true);
          if (ts.docFreq() > 0) {
            df += ts.docFreq();
            ttf += ts.totalTermFreq();
          }
        }
      }
      return new TermStats(term.bytes(), df, ttf);
    }

    @Override
    public FieldStats fieldStats(String field) throws IOException {
      long maxDoc = 0, docCount = 0, sumTtf = 0, sumDf = 0;
      for (IndexSearcher s : shards) {
        FieldStats fs = s == shards[myShard] ? super.fieldStats(field) : s.fieldStats(field);
        if (fs != null) {
          maxDoc += fs.maxDoc();
          docCount += fs.docCount();
          sumTtf += fs.sumTotalTermFreq();
          sumDf += fs.sumDocFreq();
        }
      }
      return docCount == 0 ? null : new FieldStats(field, maxDoc, docCount, sumTtf, sumDf);
    }
  }

  private Directory dir0, dir1;
  private IndexReader r0, r1, global;
  private IndexSearcher shardSearcher, shard1Searcher, globalSearcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir0 = newDirectory();
    dir1 = newDirectory();
    index(dir0, new String[] {"A"}, new String[] {"quick fox"}, new String[] {"red"});
    index(
        dir1,
        new String[] {"B", "C", "D"},
        new String[] {"fast dog", "fast cat", "fast fast bird"},
        new String[] {"fox", "fox", "bird"});
    r0 = DirectoryReader.open(dir0);
    r1 = DirectoryReader.open(dir1);
    global = new MultiReader(new IndexReader[] {r0, r1}, false);
    IndexSearcher s0 = new IndexSearcher(r0);
    IndexSearcher s1 = new IndexSearcher(r1);
    shardSearcher = new DistributedStatsSearcher(new IndexSearcher[] {s0, s1}, 0);
    shard1Searcher = new DistributedStatsSearcher(new IndexSearcher[] {s0, s1}, 1);
    globalSearcher = new IndexSearcher(global);
    for (IndexSearcher s : new IndexSearcher[] {s0, s1, shardSearcher, shard1Searcher, globalSearcher}) {
      s.setSimilarity(new BM25Similarity());
      s.setQueryCache(null);
    }
  }

  private static void index(Directory dir, String[] ids, String[] body, String[] title)
      throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
    iwc.setSimilarity(new BM25Similarity());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < ids.length; i++) {
        Document d = new Document();
        d.add(new StringField("id", ids[i], Field.Store.YES));
        d.add(new TextField("body", body[i], Field.Store.NO));
        d.add(new TextField("title", title[i], Field.Store.NO));
        w.addDocument(d);
      }
    }
  }

  @Override
  public void tearDown() throws Exception {
    global.close();
    r0.close();
    r1.close();
    dir0.close();
    dir1.close();
    super.tearDown();
  }

  /** Score of doc "A" (shard 0, local doc 0; global doc 0) on the shard and in the collection. */
  private void assertShardScoreEqualsGlobal(Query q) throws IOException {
    TopDocs shardHits = shardSearcher.search(q, 10);
    TopDocs globalHits = globalSearcher.search(q, 10);
    float shard = Float.NaN, glob = Float.NaN;
    for (ScoreDoc sd : shardHits.scoreDocs) {
      if (sd.doc == 0) shard = sd.score;
    }
    for (ScoreDoc sd : globalHits.scoreDocs) {
      if (sd.doc == 0) glob = sd.score; // doc 0 of the MultiReader is doc 0 of shard 0
    }
    float shardExpl = shardSearcher.explain(q, 0).getValue().floatValue();
    float globalExpl = globalSearcher.explain(q, 0).getValue().floatValue();
    assertEquals(
        q + ": doc A scores " + shard + " on the shard (explain " + shardExpl
            + ") but " + glob + " in the whole collection (explain " + globalExpl + ")",
        glob,
        shard,
        0f);
    assertEquals(q + ": explain value", globalExpl, shardExpl, 0f);
  }

  /** Control: the override is honoured when every query term exists on the shard. */
  public void testTermQueryControl() throws IOException {
    assertShardScoreEqualsGlobal(new TermQuery(new Term("body", "quick")));
  }

  /** Control: two terms that both exist on the shard. */
  public void testBooleanQueryControl() throws IOException {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    b.add(new TermQuery(new Term("body", "quick")), BooleanClause.Occur.SHOULD);
    b.add(new TermQuery(new Term("body", "fast")), BooleanClause.Occur.SHOULD);
    assertShardScoreEqualsGlobal(b.build());
  }

  /** quick and its synonym fast: "fast" occurs only on shard 1. */
  public void testSynonymQuery() throws IOException {
    assertShardScoreEqualsGlobal(
        new SynonymQuery.Builder("body")
            .addTerm(new Term("body", "quick"))
            .addTerm(new Term("body", "fast"))
            .build());
  }

  /** "(quick|fast) fox": the idf of the phrase sums over all terms, "fast" is left out. */
  public void testMultiPhraseQuery() throws IOException {
    MultiPhraseQuery.Builder b = new MultiPhraseQuery.Builder();
    b.add(new Term[] {new Term("body", "quick"), new Term("body", "fast")});
    b.add(new Term("body", "fox"));
    assertShardScoreEqualsGlobal(b.build());
  }

  /** spanOr(quick, fast). */
  public void testSpanOrQuery() throws IOException {
    assertShardScoreEqualsGlobal(
        new SpanOrQuery(
            new SpanTermQuery(new Term("body", "quick")),
            new SpanTermQuery(new Term("body", "fast"))));
  }

  /** "fox" over body and title: title:fox occurs only on shard 1. */
  public void testCombinedFieldQuery() throws IOException {
    assertShardScoreEqualsGlobal(
        new CombinedFieldQuery.Builder("fox").addField("body").addField("title").build());
  }

  /**
   * Merged ranking: doc A ("quick fox", shard 0) and doc B ("fast dog", shard 1, local doc 0) are
   * equally good synonym matches (one occurrence, two tokens) and tie in the whole collection. With
   * distributed statistics each shard should give them that tied score; shard 0 does not.
   */
  public void testSynonymQueryMergedRanking() throws IOException {
    Query q =
        new SynonymQuery.Builder("body")
            .addTerm(new Term("body", "quick"))
            .addTerm(new Term("body", "fast"))
            .build();
    float globalA = globalSearcher.explain(q, 0).getValue().floatValue();
    float globalB = globalSearcher.explain(q, 1).getValue().floatValue(); // B = MultiReader doc 1
    assertEquals("whole collection: A and B tie", globalA, globalB, 0f);
    float shard1B = shard1Searcher.explain(q, 0).getValue().floatValue();
    assertEquals("shard 1 scores B like the whole collection", globalB, shard1B, 0f);
    float shard0A = shardSearcher.explain(q, 0).getValue().floatValue();
    assertEquals(
        "merged results: A scores " + shard0A + " on shard 0, B scores " + shard1B
            + " on shard 1; in the whole collection both score " + globalA,
        shard1B,
        shard0A,
        0f);
  }
}
