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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Sharded grouping as the package documentation describes it (merge the top groups and the top documents per group
 * yourself) with an "all remaining" size, Integer.MAX_VALUE. Two shards: shard 0 holds groups a (p=1, 3) and b (5),
 * shard 1 holds a (2) and c (4, 6); groups and documents are sorted by p.
 */
public class TestSearchGroupMergeNoLimit extends LuceneTestCase {
  static final int MAX = Integer.MAX_VALUE;
  static final Sort SORT = new Sort(new SortField("p", SortField.Type.LONG));

  private final List<Directory> dirs = new ArrayList<>();
  private final List<DirectoryReader> readers = new ArrayList<>();
  private final List<IndexSearcher> shards = new ArrayList<>();
  private final List<Collection<SearchGroup<BytesRef>>> shardGroups = new ArrayList<>();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    String[][][] data = {{{"a", "1"}, {"a", "3"}, {"b", "5"}}, {{"a", "2"}, {"c", "4"}, {"c", "6"}}};
    for (String[][] shard : data) {
      Directory dir = newDirectory();
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String[] d : shard) {
          Document doc = new Document();
          doc.add(new SortedDocValuesField("g", new BytesRef(d[0])));
          doc.add(new NumericDocValuesField("p", Long.parseLong(d[1])));
          w.addDocument(doc);
        }
      }
      DirectoryReader reader = DirectoryReader.open(dir);
      IndexSearcher searcher = new IndexSearcher(reader);
      dirs.add(dir);
      readers.add(reader);
      shards.add(searcher);
      // first pass on each shard
      shardGroups.add(
          searcher.search(
              MatchAllDocsQuery.INSTANCE,
              new FirstPassGroupingCollectorManager<>(() -> new TermGroupSelector("g"), SORT, 0, 10)));
    }
  }

  @Override
  public void tearDown() throws Exception {
    for (DirectoryReader r : readers) r.close();
    for (Directory d : dirs) d.close();
    super.tearDown();
  }

  static String render(Collection<SearchGroup<BytesRef>> groups) {
    StringBuilder sb = new StringBuilder("[");
    for (SearchGroup<BytesRef> g : groups) sb.append(g.groupValue.utf8ToString()).append(' ');
    return sb.append(']').toString();
  }

  static String render(TopGroups<BytesRef> tg) {
    StringBuilder sb = new StringBuilder("[");
    for (GroupDocs<BytesRef> g : tg.groups) {
      sb.append(g.groupValue().utf8ToString()).append('{');
      for (ScoreDoc sd : g.scoreDocs()) sb.append(sd.shardIndex).append('/').append(sd.doc).append(' ');
      sb.append('}');
    }
    return sb.append(']').toString();
  }

  private String merge(int offset, int topN) {
    try {
      return render(SearchGroup.merge(shardGroups, offset, topN, SORT));
    } catch (Throwable t) {
      throw new AssertionError(
          "SearchGroup.merge(offset=" + offset + ", topN=" + (topN == MAX ? "MAX_VALUE" : topN) + ") threw " + t, t);
    }
  }

  public void testOffset0TopNMaxValue() {
    // all merged groups: the same as topN = 10 (there are 3 groups)
    assertEquals(merge(0, 10), merge(0, MAX));
  }

  public void testOffset1TopNMaxValue() {
    // every group after the first: the same as topN = 10
    assertEquals(merge(1, 10), merge(1, MAX));
  }
}
