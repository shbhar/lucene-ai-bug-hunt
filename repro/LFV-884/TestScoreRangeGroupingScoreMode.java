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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Grouping hits by ranges of their own score: DoubleRangeGroupSelector over
 * DoubleValuesSource.SCORES.
 *
 * <p>Five documents with tags {a}, {a,b}, {a,b,c}, {a}, {a,b,c}. The query a OR b OR c of
 * constant-score clauses scores them 1, 2, 3, 1, 3, so ranges of width 1 must give [1,2) = {0, 3},
 * [2,3) = {1} and [3,4) = {2, 4}, whatever the sort. A plain IndexSearcher is used on purpose (no
 * test-framework wrapper), so the failures are what applications see.
 */
public class TestScoreRangeGroupingScoreMode extends LuceneTestCase {

  private static final String[][] TAGS = {
    {"a"}, {"a", "b"}, {"a", "b", "c"}, {"a"}, {"a", "b", "c"}
  };
  private static final String EXPECTED =
      "{DoubleRange(1.0, 2.0)=[0, 3], DoubleRange(2.0, 3.0)=[1], DoubleRange(3.0, 4.0)=[2, 4]}";
  private static final Sort BY_ID = new Sort(new SortField("id", SortField.Type.INT));

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < TAGS.length; i++) {
        Document d = new Document();
        for (String t : TAGS[i]) {
          d.add(new StringField("tag", t, Field.Store.NO));
        }
        d.add(new NumericDocValuesField("id", i));
        w.addDocument(d);
      }
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private static Query query() {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (String t : new String[] {"a", "b", "c"}) {
      b.add(new ConstantScoreQuery(new TermQuery(new Term("tag", t))), Occur.SHOULD);
    }
    return b.build();
  }

  private static GroupSelector<DoubleRange> scoreRanges() {
    return new DoubleRangeGroupSelector(
        DoubleValuesSource.SCORES, new DoubleRangeFactory(0, 1, 10));
  }

  private static GroupingSearch grouping() {
    GroupingSearch g = new GroupingSearch(() -> scoreRanges());
    g.setGroupDocsLimit(10);
    return g;
  }

  private <T> String groups(GroupingSearch g) throws Exception {
    TopGroups<T> tg = g.search(searcher, query(), 0, 10);
    Map<String, List<Integer>> m = new TreeMap<>();
    for (GroupDocs<T> gd : tg.groups) {
      List<Integer> docs = new ArrayList<>();
      for (ScoreDoc sd : gd.scoreDocs()) {
        docs.add(sd.doc);
      }
      Collections.sort(docs);
      m.put(String.valueOf(gd.groupValue()), docs);
    }
    return m.toString();
  }

  /** Control: the plain scores, and GroupingSearch with its defaults. Passes today. */
  public void testDefaultsControl() throws Exception {
    TopDocs td = searcher.search(query(), 10);
    float[] scores = new float[TAGS.length];
    for (ScoreDoc sd : td.scoreDocs) {
      scores[sd.doc] = sd.score;
    }
    assertEquals("[1.0, 2.0, 3.0, 1.0, 3.0]", Arrays.toString(scores));
    assertEquals(EXPECTED, groups(grouping()));
  }

  /** Workaround: a score tie-break in the group sort makes the collectors ask for scores. */
  public void testScoreTieBreakWorkaround() throws Exception {
    GroupingSearch g = grouping();
    g.setGroupSort(new Sort(new SortField("id", SortField.Type.INT), SortField.FIELD_SCORE));
    assertEquals(EXPECTED, groups(g));
  }

  /** Trigger 1: the only change from the defaults is a group sort by a field. */
  public void testFieldGroupSort() throws Exception {
    GroupingSearch g = grouping();
    g.setGroupSort(BY_ID);
    assertEquals(EXPECTED, groups(g));
  }

  /** Trigger 1 with LongRangeGroupSelector over the score as a long. */
  public void testLongRangeSelectorFieldGroupSort() throws Exception {
    GroupingSearch g =
        new GroupingSearch(
            () ->
                new LongRangeGroupSelector(
                    DoubleValuesSource.SCORES.toLongValuesSource(),
                    new LongRangeFactory(0, 1, 10)));
    g.setGroupDocsLimit(10);
    g.setGroupSort(BY_ID);
    assertEquals(
        "{LongRange(1, 2)=[0, 3], LongRange(2, 3)=[1], LongRange(3, 4)=[2, 4]}", groups(g));
  }

  /**
   * Trigger 2: relevance group sort (default), but includeMaxScore=false and a field sort inside.
   */
  public void testNoMaxScoreWithFieldSortWithinGroup() throws Exception {
    GroupingSearch g = grouping();
    g.setSortWithinGroup(BY_ID);
    g.setIncludeMaxScore(false);
    assertEquals(EXPECTED, groups(g));
  }

  /** Trigger 3: AllGroupsCollectorManager on its own lists the distinct score ranges. */
  public void testAllGroupsCollectorManagerAlone() throws Exception {
    Collection<DoubleRange> all =
        searcher.search(query(), new AllGroupsCollectorManager<>(() -> scoreRanges()));
    Set<String> got = new TreeSet<>();
    for (DoubleRange r : all) {
      got.add(String.valueOf(r));
    }
    assertEquals(
        "[DoubleRange(1.0, 2.0), DoubleRange(2.0, 3.0), DoubleRange(3.0, 4.0)]", got.toString());
  }

  /** Trigger 3: AllGroupHeadsCollectorManager on its own, head = lowest id of each range. */
  public void testAllGroupHeadsCollectorManagerAlone() throws Exception {
    int[] heads =
        searcher
            .search(query(), new AllGroupHeadsCollectorManager<>(() -> scoreRanges(), BY_ID))
            .retrieveGroupHeads();
    Arrays.sort(heads);
    assertEquals("[0, 1, 2]", Arrays.toString(heads));
  }
}
