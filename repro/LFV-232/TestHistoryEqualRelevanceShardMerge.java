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
import java.util.Collection;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queries.function.FunctionQuery;
import org.apache.lucene.queries.function.valuesource.IntFieldSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.LuceneTestCase;

/**
 * History variant for the 8.x/9.0 grouping API (GroupSelector collectors, no getScores flag). Two-pass
 * grouping over two shards merged with TopGroups.merge, as the grouping package documentation
 * describes for sharded search. One group "g". Shard 0 holds docs scoring 5 and 1, shard 1 a doc
 * scoring 4 (score = the "score" doc value). The merged top 2 documents of the group must be
 * shard0/doc0 (5) and shard1/doc0 (4), and the merged maxScore must be 5, for every within-group
 * sort that equals Sort.RELEVANCE.
 */
public class TestHistoryEqualRelevanceShardMerge extends LuceneTestCase {

  private static final String EXPECTED_PAGE = "shard0/doc0, shard1/doc0";

  public void testRelevanceConstantControl() throws Exception {
    Result r = shardMerge(Sort.RELEVANCE);
    assertEquals("merged top 2", EXPECTED_PAGE, r.page);
    assertEquals("merged maxScore", 5f, r.maxScore, 0f);
  }

  public void testEqualSortFieldScoreDocs() throws Exception {
    Sort sort = new Sort(SortField.FIELD_SCORE);
    assertEquals(Sort.RELEVANCE, sort);
    assertEquals("merged top 2", EXPECTED_PAGE, shardMerge(sort).page);
  }

  public void testEqualNewSortDocs() throws Exception {
    Sort sort = new Sort();
    assertEquals(Sort.RELEVANCE, sort);
    assertEquals("merged top 2", EXPECTED_PAGE, shardMerge(sort).page);
  }

  public void testEqualSortFieldScoreMaxScore() throws Exception {
    Sort sort = new Sort(SortField.FIELD_SCORE);
    assertEquals("merged maxScore", 5f, shardMerge(sort).maxScore, 0f);
  }

  private static final class Result {
    final String page;
    final float maxScore;

    Result(String page, float maxScore) {
      this.page = page;
      this.maxScore = maxScore;
    }
  }

  private static Directory shard(int... scores) throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(null));
    for (int s : scores) {
      Document d = new Document();
      d.add(new SortedDocValuesField("group", new BytesRef("g")));
      d.add(new NumericDocValuesField("score", s));
      w.addDocument(d);
    }
    w.close();
    return dir;
  }

  @SuppressWarnings("unchecked")
  private static Result shardMerge(Sort withinGroupSort) throws Exception {
    Query query = new FunctionQuery(new IntFieldSource("score"));
    Directory d0 = shard(5, 1);
    Directory d1 = shard(4);
    DirectoryReader r0 = DirectoryReader.open(d0);
    DirectoryReader r1 = DirectoryReader.open(d1);
    try {
      IndexSearcher[] shards = {new IndexSearcher(r0), new IndexSearcher(r1)};
      List<Collection<SearchGroup<BytesRef>>> first = new ArrayList<>();
      for (IndexSearcher s : shards) {
        FirstPassGroupingCollector<BytesRef> c =
            new FirstPassGroupingCollector<>(new TermGroupSelector("group"), Sort.RELEVANCE, 10);
        s.search(query, c);
        first.add(c.getTopGroups(0));
      }
      Collection<SearchGroup<BytesRef>> groups = SearchGroup.merge(first, 0, 10, Sort.RELEVANCE);
      TopGroups<BytesRef>[] second = new TopGroups[shards.length];
      for (int i = 0; i < shards.length; i++) {
        TopGroupsCollector<BytesRef> c =
            new TopGroupsCollector<>(
                new TermGroupSelector("group"), groups, Sort.RELEVANCE, withinGroupSort, 2, true);
        shards[i].search(query, c);
        second[i] = c.getTopGroups(0);
      }
      TopGroups<BytesRef> merged =
          TopGroups.merge(second, Sort.RELEVANCE, withinGroupSort, 0, 2, TopGroups.ScoreMergeMode.None);
      GroupDocs<BytesRef> gd = merged.groups[0];
      StringBuilder page = new StringBuilder();
      for (int i = 0; i < gd.scoreDocs.length; i++) {
        if (i > 0) page.append(", ");
        page.append("shard").append(gd.scoreDocs[i].shardIndex).append("/doc").append(gd.scoreDocs[i].doc);
      }
      return new Result(page.toString(), gd.maxScore);
    } finally {
      r0.close();
      r1.close();
      d0.close();
      d1.close();
    }
  }
}
