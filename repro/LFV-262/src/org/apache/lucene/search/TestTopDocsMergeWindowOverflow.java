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

import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * TopDocs.merge(start, topN, ...) computes {@code start + topN} in int. With a page offset {@code
 * start > 0} and a "give me everything" {@code topN} such as Integer.MAX_VALUE the sum wraps to a
 * negative number: the merge loop never runs and the returned array is all nulls.
 */
public class TestTopDocsMergeWindowOverflow extends LuceneTestCase {

  private static TopDocs[] shards() {
    TopDocs shard0 =
        new TopDocs(
            new TotalHits(3, TotalHits.Relation.EQUAL_TO),
            new ScoreDoc[] {new ScoreDoc(0, 3f, 0), new ScoreDoc(1, 2f, 0), new ScoreDoc(2, 1f, 0)});
    TopDocs shard1 =
        new TopDocs(
            new TotalHits(2, TotalHits.Relation.EQUAL_TO),
            new ScoreDoc[] {new ScoreDoc(0, 2.5f, 1), new ScoreDoc(1, 0.5f, 1)});
    return new TopDocs[] {shard0, shard1};
  }

  public void testMergeSecondPageWithMaxValueTopN() {
    TopDocs[] shards = shards();
    // control: the same page with a topN that does not overflow
    TopDocs expected = TopDocs.merge(1, 4, shards);
    assertEquals(4, expected.scoreDocs.length);

    TopDocs actual = TopDocs.merge(1, Integer.MAX_VALUE, shards);
    assertEquals(4, actual.scoreDocs.length);
    for (int i = 0; i < expected.scoreDocs.length; i++) {
      assertSame("hit " + i, expected.scoreDocs[i], actual.scoreDocs[i]);
    }
  }

  public void testFieldDocMergeSecondPageWithMaxValueTopN() {
    Sort sort = new Sort(new SortField("f", SortField.Type.INT));
    TopFieldDocs[] shards = {
      new TopFieldDocs(
          new TotalHits(2, TotalHits.Relation.EQUAL_TO),
          new FieldDoc[] {
            new FieldDoc(0, Float.NaN, new Object[] {1}, 0),
            new FieldDoc(1, Float.NaN, new Object[] {3}, 0)
          },
          sort.getSort()),
      new TopFieldDocs(
          new TotalHits(1, TotalHits.Relation.EQUAL_TO),
          new FieldDoc[] {new FieldDoc(0, Float.NaN, new Object[] {2}, 1)},
          sort.getSort())
    };
    TopFieldDocs expected = TopDocs.merge(sort, 1, 2, shards);
    assertEquals(2, expected.scoreDocs.length);

    TopFieldDocs actual = TopDocs.merge(sort, 1, Integer.MAX_VALUE, shards);
    assertEquals(2, actual.scoreDocs.length);
    for (int i = 0; i < expected.scoreDocs.length; i++) {
      assertSame("hit " + i, expected.scoreDocs[i], actual.scoreDocs[i]);
    }
  }
}
