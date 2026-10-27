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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * LongRangeGroupSelector and DoubleRangeGroupSelector read their per-segment values only in
 * setScorer(), while the GroupSelector contract is "for each segment, call setNextReader and then
 * advanceTo for each matching document". Two public, documented paths follow that contract without
 * calling setScorer on the selector, and both fail with NullPointerException although nothing in
 * the search needs scores (field sorts, no max score, score-independent value sources):
 *
 * <ul>
 *   <li>GroupingSearch.setCaching(maxDocs, cacheScores=false): the second pass is replayed from the
 *       cache, and CachingCollector#replay documents that no Scorer is set when scores are not
 *       cached;
 *   <li>DistinctValuesCollector(Manager) with a range selector as the value selector: the per-group
 *       ValuesCollector forwards setNextReader to the value selector but never setScorer.
 * </ul>
 *
 * Every test compares against the same search with a path that does call setScorer.
 */
public class TestRangeGroupSelectorWithoutScorer extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      // two segments: values 5, 15, 25 / 35, 45, 55; group field "g" alternates A, B
      long[][] segments = {{5, 15, 25}, {35, 45, 55}};
      int id = 0;
      for (long[] seg : segments) {
        for (long v : seg) {
          Document d = new Document();
          d.add(new NumericDocValuesField("id", id));
          d.add(new NumericDocValuesField("price", v));
          d.add(new DoubleDocValuesField("weight", v / 10.0));
          d.add(new SortedDocValuesField("g", new BytesRef(id % 2 == 0 ? "A" : "B")));
          w.addDocument(d);
          id++;
        }
        w.commit();
      }
    }
    reader = DirectoryReader.open(dir);
    assertEquals(2, reader.leaves().size());
    searcher = new IndexSearcher(reader); // sequential, no executor
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private static final Sort BY_ID = new Sort(new SortField("id", SortField.Type.LONG));

  private static Supplier<GroupSelector<?>> longRanges() {
    return () ->
        new LongRangeGroupSelector(
            LongValuesSource.fromLongField("price"), new LongRangeFactory(0, 20, 60));
  }

  private static Supplier<GroupSelector<?>> doubleRanges() {
    return () ->
        new DoubleRangeGroupSelector(
            DoubleValuesSource.fromDoubleField("weight"), new DoubleRangeFactory(0, 2, 6));
  }

  private String groupedSearch(Supplier<GroupSelector<?>> selector, boolean cacheWithoutScores)
      throws IOException {
    GroupingSearch gs = new GroupingSearch(selector);
    // nothing needs scores: field sorts and no max score
    gs.setGroupSort(BY_ID).setSortWithinGroup(BY_ID).setGroupDocsLimit(10).setIncludeMaxScore(false);
    if (cacheWithoutScores) {
      gs.setCaching(100, false);
    }
    TopGroups<Object> tg = gs.search(searcher, MatchAllDocsQuery.INSTANCE, 0, 10);
    StringBuilder sb = new StringBuilder();
    for (GroupDocs<Object> g : tg.groups) {
      sb.append(g.groupValue()).append(" -> [");
      for (ScoreDoc sd : g.scoreDocs()) {
        sb.append(sd.doc).append(' ');
      }
      sb.append("] ");
    }
    return sb.toString();
  }

  /** GroupingSearch with a LongRangeGroupSelector and caching without scores. */
  public void testLongRangeGroupingWithCachingWithoutScores() throws IOException {
    String expected = groupedSearch(longRanges(), false);
    assertEquals(
        "LongRange(0, 20) -> [0 1 ] LongRange(20, 40) -> [2 3 ] LongRange(40, 60) -> [4 5 ] ",
        expected);
    // documented: setCaching(maxDocsToCache, cacheScores); the cache holds all 6 docs
    assertEquals(expected, groupedSearch(longRanges(), true));
  }

  /** Same with a DoubleRangeGroupSelector. */
  public void testDoubleRangeGroupingWithCachingWithoutScores() throws IOException {
    String expected = groupedSearch(doubleRanges(), false);
    assertEquals(
        "DoubleRange(0.0, 2.0) -> [0 1 ] DoubleRange(2.0, 4.0) -> [2 3 ] DoubleRange(4.0, 6.0) -> [4 5 ] ",
        expected);
    assertEquals(expected, groupedSearch(doubleRanges(), true));
  }

  /** DistinctValuesCollectorManager with a LongRangeGroupSelector as the value selector. */
  public void testDistinctValuesWithLongRangeValueSelector() throws IOException {
    List<SearchGroup<BytesRef>> groups = new ArrayList<>();
    for (String g : new String[] {"A", "B"}) {
      SearchGroup<BytesRef> sg = new SearchGroup<>();
      sg.groupValue = new BytesRef(g);
      groups.add(sg);
    }
    List<DistinctValuesCollector.GroupCount<BytesRef, LongRange>> counts =
        searcher.search(
            MatchAllDocsQuery.INSTANCE,
            new DistinctValuesCollectorManager<BytesRef, LongRange>(
                () -> new TermGroupSelector("g"),
                groups,
                () ->
                    new LongRangeGroupSelector(
                        LongValuesSource.fromLongField("price"), new LongRangeFactory(0, 20, 60))));
    StringBuilder sb = new StringBuilder();
    for (DistinctValuesCollector.GroupCount<BytesRef, LongRange> gc : counts) {
      TreeSet<String> values = new TreeSet<>();
      for (LongRange r : gc.uniqueValues) {
        values.add(String.valueOf(r));
      }
      sb.append(gc.groupValue.utf8ToString()).append('=').append(values).append(' ');
    }
    // A: docs 0, 2, 4 (prices 5, 25, 45); B: docs 1, 3, 5 (prices 15, 35, 55)
    assertEquals(
        "A=[LongRange(0, 20), LongRange(20, 40), LongRange(40, 60)] "
            + "B=[LongRange(0, 20), LongRange(20, 40), LongRange(40, 60)] ",
        sb.toString());
  }
}
