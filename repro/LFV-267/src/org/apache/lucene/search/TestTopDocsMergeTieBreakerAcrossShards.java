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

import java.util.Comparator;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * TopDocs.merge(start, topN, shardHits, tieBreaker) accepts any Comparator as tie breaker. When the
 * tie breaker returns 0 for two hits of different shards that sit at the same position in their
 * shard (here: both are the first hit), tieBreakLessThan asserts first.hitIndex !=
 * second.hitIndex, which does not hold, so the merge throws AssertionError with assertions on.
 */
public class TestTopDocsMergeTieBreakerAcrossShards extends LuceneTestCase {

  public void testTieBreakerReturningZeroForDifferentShards() {
    // two shards, each with one hit; same score, same shard-local doc id
    TopDocs shard0 =
        new TopDocs(
            new TotalHits(1, TotalHits.Relation.EQUAL_TO), new ScoreDoc[] {new ScoreDoc(0, 1f, 0)});
    TopDocs shard1 =
        new TopDocs(
            new TotalHits(1, TotalHits.Relation.EQUAL_TO), new ScoreDoc[] {new ScoreDoc(0, 1f, 1)});
    // a legal tie breaker that ignores the shard index
    Comparator<ScoreDoc> byDocOnly = Comparator.comparingInt(sd -> sd.doc);

    TopDocs merged = TopDocs.merge(0, 2, new TopDocs[] {shard0, shard1}, byDocOnly);

    assertEquals(2, merged.scoreDocs.length);
    assertNotSame(merged.scoreDocs[0], merged.scoreDocs[1]);
  }

  public void testFieldDocTieBreakerReturningZeroForDifferentShards() {
    Sort sort = new Sort(new SortField("f", SortField.Type.INT));
    TopFieldDocs[] shards = {
      new TopFieldDocs(
          new TotalHits(1, TotalHits.Relation.EQUAL_TO),
          new FieldDoc[] {new FieldDoc(0, Float.NaN, new Object[] {7}, 0)},
          sort.getSort()),
      new TopFieldDocs(
          new TotalHits(1, TotalHits.Relation.EQUAL_TO),
          new FieldDoc[] {new FieldDoc(0, Float.NaN, new Object[] {7}, 1)},
          sort.getSort())
    };
    Comparator<ScoreDoc> byDocOnly = Comparator.comparingInt(sd -> sd.doc);

    TopFieldDocs merged = TopDocs.merge(sort, 0, 2, shards, byDocOnly);

    assertEquals(2, merged.scoreDocs.length);
    assertNotSame(merged.scoreDocs[0], merged.scoreDocs[1]);
  }
}
