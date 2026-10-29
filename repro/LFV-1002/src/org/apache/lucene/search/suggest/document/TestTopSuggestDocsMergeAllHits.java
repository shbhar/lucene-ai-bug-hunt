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
package org.apache.lucene.search.suggest.document;

import org.apache.lucene.search.TotalHits;
import org.apache.lucene.search.suggest.document.TopSuggestDocs.SuggestScoreDoc;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTopSuggestDocsMergeAllHits extends LuceneTestCase {

  private static TopSuggestDocs shard(String key, float score) {
    return new TopSuggestDocs(
        new TotalHits(1, TotalHits.Relation.EQUAL_TO),
        new SuggestScoreDoc[] {new SuggestScoreDoc(0, key, null, score)});
  }

  public void testMergeWithTopNMaxValue() {
    TopSuggestDocs[] shards = {shard("a", 1f), shard("b", 2f)};
    try {
      assertEquals(2, TopSuggestDocs.merge(Integer.MAX_VALUE, shards).scoreLookupDocs().length);
    } catch (IllegalArgumentException e) {
      // expected: both hits, as with topN = 10 and as TopDocs.merge returns; actual: the queue is pre-sized by topN
      throw new AssertionError("expected the 2 merged suggestions for topN = Integer.MAX_VALUE, but got " + e, e);
    }
  }
}
