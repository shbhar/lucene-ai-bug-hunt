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
import org.apache.lucene.queries.function.FunctionScoreQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * 9.x API variant (Collector API, TopGroups.merge over an array): sharded two-pass grouping as the
 * package documentation describes, with withinGroupSort = new Sort(SortField.FIELD_SCORE), which
 * equals Sort.RELEVANCE. Shard 0 holds docs with scores 5 and 1, shard 1 a doc with score 4. The
 * merged top 2 documents must be the ones scoring 5 and 4.
 */
public class TestEqualRelevanceSortShardMerge9x extends LuceneTestCase {

  private static Directory shard(int... scores) throws Exception {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int s : scores) {
        Document d = new Document();
        d.add(new SortedDocValuesField("group", new BytesRef("g")));
        d.add(new NumericDocValuesField("score", s));
        w.addDocument(d);
      }
    }
    return dir;
  }

  public void testShardMergeWithRelevanceInstanceControl() throws Exception {
    assertEquals("merged top 2", "shard0/doc0, shard1/doc0", shardMergePage(Sort.RELEVANCE));
  }

  public void testShardMergeWithEqualRelevanceSort() throws Exception {
    Sort withinGroupSort = new Sort(SortField.FIELD_SCORE);
    assertEquals(Sort.RELEVANCE, withinGroupSort);
    assertEquals("merged top 2", "shard0/doc0, shard1/doc0", shardMergePage(withinGroupSort));
  }

  @SuppressWarnings("unchecked")
  private static String shardMergePage(Sort withinGroupSort) throws Exception {
    Query query =
        new FunctionScoreQuery(new MatchAllDocsQuery(), DoubleValuesSource.fromIntField("score"));
    Directory d0 = shard(5, 1);
    Directory d1 = shard(4);
    try (DirectoryReader r0 = DirectoryReader.open(d0);
        DirectoryReader r1 = DirectoryReader.open(d1)) {
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
      // shard 0 / doc 0 has score 5, shard 1 / doc 0 has score 4
      return "shard" + gd.scoreDocs[0].shardIndex + "/doc" + gd.scoreDocs[0].doc + ", shard"
          + gd.scoreDocs[1].shardIndex + "/doc" + gd.scoreDocs[1].doc;
    } finally {
      d0.close();
      d1.close();
    }
  }
}
