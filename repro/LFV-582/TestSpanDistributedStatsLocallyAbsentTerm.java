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
package org.apache.lucene.queries.spans;

import java.io.IOException;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.FieldStats;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermStats;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Span queries build their similarity input in SpanWeight#buildSimWeight, which calls
 * IndexSearcher#termStats only for terms whose docFreq on the local shard is greater than 0. With a
 * termStats/fieldStats override that returns collection-wide statistics, a span-or clause whose
 * term occurs only on another shard is therefore left out. Shard 0 holds "quick fox"; shard 1 holds
 * "fast dog", "fast cat", "fast fast bird"; body:fast exists only on shard 1.
 */
public class TestSpanDistributedStatsLocallyAbsentTerm extends LuceneTestCase {

  /** Statistics summed over all shards, as in ShardSearchingTestBase. */
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
  private IndexSearcher shard0Searcher, globalSearcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir0 = newDirectory();
    dir1 = newDirectory();
    index(dir0, "quick fox");
    index(dir1, "fast dog", "fast cat", "fast fast bird");
    r0 = DirectoryReader.open(dir0);
    r1 = DirectoryReader.open(dir1);
    global = new MultiReader(new IndexReader[] {r0, r1}, false);
    IndexSearcher[] shards = {new IndexSearcher(r0), new IndexSearcher(r1)};
    shard0Searcher = new DistributedStatsSearcher(shards, 0);
    globalSearcher = new IndexSearcher(global);
    for (IndexSearcher s :
        new IndexSearcher[] {shards[0], shards[1], shard0Searcher, globalSearcher}) {
      s.setSimilarity(new BM25Similarity());
      s.setQueryCache(null);
    }
  }

  private static void index(Directory dir, String... bodies) throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
    iwc.setSimilarity(new BM25Similarity());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (String body : bodies) {
        Document d = new Document();
        d.add(new TextField("body", body, Field.Store.NO));
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

  /** Doc "quick fox" is doc 0 of shard 0 and doc 0 of the MultiReader. */
  private void assertShardScoreEqualsGlobal(Query q) throws IOException {
    float shard = shard0Searcher.explain(q, 0).getValue().floatValue();
    float whole = globalSearcher.explain(q, 0).getValue().floatValue();
    assertEquals(
        q + ": doc A scores " + shard + " on the shard but " + whole + " in the whole collection",
        whole,
        shard,
        0f);
  }

  private static SpanQuery quickOrFast() {
    return new SpanOrQuery(
        new SpanTermQuery(new Term("body", "quick")), new SpanTermQuery(new Term("body", "fast")));
  }

  /** Control: a span query whose terms all exist on the shard. */
  public void testSpanNearControl() throws IOException {
    assertShardScoreEqualsGlobal(
        new SpanNearQuery(
            new SpanQuery[] {
              new SpanTermQuery(new Term("body", "quick")),
              new SpanTermQuery(new Term("body", "fox"))
            },
            0,
            true));
  }

  public void testSpanOrQuery() throws IOException {
    assertShardScoreEqualsGlobal(quickOrFast());
  }

  /** The same loss inside another span query: spanNear([spanOr(quick, fast), fox]). */
  public void testSpanNearOverSpanOr() throws IOException {
    assertShardScoreEqualsGlobal(
        new SpanNearQuery(
            new SpanQuery[] {quickOrFast(), new SpanTermQuery(new Term("body", "fox"))}, 0, true));
  }
}
