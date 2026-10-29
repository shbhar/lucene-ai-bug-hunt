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

import java.util.List;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestTopGroupsMergeOffsetOverflow extends LuceneTestCase {

  @SuppressWarnings("unchecked")
  private static TopGroups<BytesRef> shard(float score) {
    GroupDocs<BytesRef> group =
        new GroupDocs<>(
            Float.NaN,
            score,
            new TotalHits(1, TotalHits.Relation.EQUAL_TO),
            new ScoreDoc[] {new ScoreDoc(0, score)},
            new BytesRef("g"),
            new Object[] {score});
    return new TopGroups<>(
        Sort.RELEVANCE.getSort(), Sort.RELEVANCE.getSort(), 1, 1, new GroupDocs[] {group}, score);
  }

  public void testDocOffsetOneWithUnboundedDocTopN() {
    TopGroups<BytesRef> merged;
    try {
      merged =
          TopGroups.merge(
              List.of(shard(2f), shard(1f)),
              Sort.RELEVANCE,
              Sort.RELEVANCE,
              1,
              Integer.MAX_VALUE,
              TopGroups.ScoreMergeMode.None);
    } catch (NegativeArraySizeException e) {
      throw new AssertionError(
          "expected group g's second document, but merge(docOffset=1, docTopN=MAX_VALUE) threw " + e,
          e);
    }
    assertEquals(1, merged.groups[0].scoreDocs().length);
  }
}
