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
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndexSearcher#termStats and #fieldStats "can be overridden for example, to return a term's
 * [field's] statistics across a distributed collection". With such an override a document on a
 * shard should score as it scores in the whole collection; TestShardSearching checks this for
 * TermQuery and PrefixQuery.
 *
 * <p>SynonymQuery, MultiPhraseQuery and CombinedFieldQuery call termStats only for terms whose
 * docFreq on the local shard is greater than 0, so a query term that occurs only on other shards
 * never reaches the override and its collection-wide statistics are left out.
 *
 * <p>Two shards. Shard 0 holds doc A (body "quick fox", title "red"). Shard 1 holds B, C, D (body
 * "fast dog", "fast cat", "fast fast bird"; title "fox", "fox", "bird"). So body:fast and title:fox
 * exist only on shard 1. The whole collection is a MultiReader over both shards.
 */
public class TestDistributedStatsLocallyAbsentTerm extends LuceneTestCase {

  /**
   * Searches one shard and scores with statistics summed over all shards (the arithmetic of
   * ShardSearchingTestBase: the local values passed in, plus the other shards' values).
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
      // null = the term exists on no shard (only reachable if the hook is also asked about
      // locally absent terms)
      return df == 0 ? null : new TermStats(term.bytes(), df, ttf);
    }

    @Override
    public FieldStats fieldStats(String field) throws IOException {
      long maxDoc = 0, docCount = 0, sumTtf = 0, sumDf = 0;
      for (int i = 0; i < shards.length; i++) {
        FieldStats fs = i == myShard ? super.fieldStats(field) : shards[i].fieldStats(field);
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
  private IndexSearcher shard0Searcher, shard1Searcher, globalSearcher;

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
    IndexSearcher[] shards = {new IndexSearcher(r0), new IndexSearcher(r1)};
    shard0Searcher = new DistributedStatsSearcher(shards, 0);
    shard1Searcher = new DistributedStatsSearcher(shards, 1);
    globalSearcher = new IndexSearcher(global);
    for (IndexSearcher s :
        new IndexSearcher[] {
          shards[0], shards[1], shard0Searcher, shard1Searcher, globalSearcher
        }) {
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

  /** Doc A is doc 0 of shard 0 and doc 0 of the MultiReader. */
  private void assertShardScoreEqualsGlobal(Query q) throws IOException {
    float shard = shard0Searcher.explain(q, 0).getValue().floatValue();
    float whole = globalSearcher.explain(q, 0).getValue().floatValue();
    float shardTop = shard0Searcher.search(q, 10).scoreDocs[0].score;
    assertEquals(q + ": explain and search disagree on the shard", shard, shardTop, 0f);
    assertEquals(
        q + ": doc A scores " + shard + " on the shard but " + whole + " in the whole collection",
        whole,
        shard,
        0f);
  }

  private static Query synonym(String... terms) {
    SynonymQuery.Builder b = new SynonymQuery.Builder("body");
    for (String t : terms) {
      b.addTerm(new Term("body", t));
    }
    return b.build();
  }

  /** Control: every query term exists on the shard. */
  public void testTermQueryControl() throws IOException {
    assertShardScoreEqualsGlobal(new TermQuery(new Term("body", "quick")));
  }

  /** Control: BooleanQuery scores each term separately, "fast" does not match on shard 0. */
  public void testBooleanQueryControl() throws IOException {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    b.add(new TermQuery(new Term("body", "quick")), BooleanClause.Occur.SHOULD);
    b.add(new TermQuery(new Term("body", "fast")), BooleanClause.Occur.SHOULD);
    assertShardScoreEqualsGlobal(b.build());
  }

  /** Control: a synonym that exists on no shard at all must not break the query. */
  public void testSynonymAbsentEverywhereControl() throws IOException {
    assertShardScoreEqualsGlobal(synonym("quick", "speedy"));
  }

  /** quick and its synonym fast: "fast" occurs only on shard 1 (docFreq 3 there). */
  public void testSynonymQuery() throws IOException {
    assertShardScoreEqualsGlobal(synonym("quick", "fast"));
  }

  /**
   * A ("quick fox", shard 0) and B ("fast dog", shard 1) tie in the whole collection, so a merged
   * top-k over both shards must tie them too.
   */
  public void testSynonymQueryMergedRanking() throws IOException {
    Query q = synonym("quick", "fast");
    float wholeA = globalSearcher.explain(q, 0).getValue().floatValue();
    float wholeB = globalSearcher.explain(q, 1).getValue().floatValue(); // B = MultiReader doc 1
    assertEquals("whole collection: A and B tie", wholeA, wholeB, 0f);
    float shard1B = shard1Searcher.explain(q, 0).getValue().floatValue();
    assertEquals("shard 1 scores B like the whole collection", wholeB, shard1B, 0f);
    float shard0A = shard0Searcher.explain(q, 0).getValue().floatValue();
    assertEquals(
        "merged top-k: A scores "
            + shard0A
            + " on shard 0, B scores "
            + shard1B
            + " on shard 1; in the whole collection both score "
            + wholeA,
        shard1B,
        shard0A,
        0f);
  }

  /** "fox" over body and title: title:fox occurs only on shard 1 (docFreq 2 there). */
  public void testCombinedFieldQuery() throws IOException {
    assertShardScoreEqualsGlobal(
        new CombinedFieldQuery.Builder("fox").addField("body").addField("title").build());
  }

  /** "(quick|fast) fox": the phrase's idf sums over all its terms, "fast" is left out. */
  public void testMultiPhraseQuery() throws IOException {
    MultiPhraseQuery.Builder b = new MultiPhraseQuery.Builder();
    b.add(new Term[] {new Term("body", "quick"), new Term("body", "fast")});
    b.add(new Term("body", "fox"));
    assertShardScoreEqualsGlobal(b.build());
  }
}
