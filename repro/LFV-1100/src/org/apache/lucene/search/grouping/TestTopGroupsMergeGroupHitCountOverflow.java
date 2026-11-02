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
package org.apache.lucene.search.grouping;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTopGroupsMergeGroupHitCountOverflow extends LuceneTestCase {

  public void testGroupHitCountSummedOverShardsAboveIntRange() {
    // Two shards, each a separate index whose group "g" has 1.5 billion hits (each <= MAX_DOCS).
    List<TopGroups<String>> shards = new ArrayList<>();
    for (int shard = 0; shard < 2; shard++) {
      int hits = 1_500_000_000;
      @SuppressWarnings("unchecked")
      GroupDocs<String>[] groups =
          new GroupDocs[] {
            new GroupDocs<>(
                Float.NaN,
                1f,
                new TotalHits(hits, TotalHits.Relation.EQUAL_TO),
                new ScoreDoc[] {new ScoreDoc(shard, 1f, shard)},
                "g",
                new Object[] {1f})
          };
      shards.add(
          new TopGroups<>(
              Sort.RELEVANCE.getSort(), Sort.RELEVANCE.getSort(), hits, hits, groups, 1f));
    }
    TopGroups<String> merged =
        TopGroups.merge(shards, Sort.RELEVANCE, Sort.RELEVANCE, 0, 10, TopGroups.ScoreMergeMode.None);
    assertEquals(3_000_000_000L, merged.groups[0].totalHits().value());
  }
}
