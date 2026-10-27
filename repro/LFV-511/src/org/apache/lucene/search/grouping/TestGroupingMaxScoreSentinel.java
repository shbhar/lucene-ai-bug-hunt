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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * TopGroups.maxScore is documented as "Highest score across all hits, or Float.NaN if scores were
 * not computed", and GroupDocs.maxScore as the max score in the group. TopGroupsCollector,
 * its per-group max-score collector and BlockGroupingCollector start their running maximum at
 * Float.MIN_VALUE (1.4E-45, the smallest positive float), so they report 1.4E-45 when no score was
 * computed or when every score is 0.
 */
public class TestGroupingMaxScoreSentinel extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      // two groups, each indexed as one block whose last document is marked groupend
      String[][] groups = {{"g1", "1", "2"}, {"g2", "3"}};
      for (String[] g : groups) {
        List<Document> block = new ArrayList<>();
        for (int i = 1; i < g.length; i++) {
          Document d = new Document();
          d.add(new StringField("body", "x", Field.Store.NO));
          d.add(new SortedDocValuesField("group", new BytesRef(g[0])));
          d.add(new NumericDocValuesField("price", Long.parseLong(g[i])));
          if (i == g.length - 1) {
            d.add(new StringField("groupend", "true", Field.Store.NO));
          }
          block.add(d);
        }
        w.addDocuments(block);
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

  private static final Sort BY_PRICE = new Sort(new SortField("price", SortField.Type.LONG));

  /** Every hit of this query scores 0 (a pure filter). */
  private static Query zeroScoreQuery() {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("body", "x")), Occur.FILTER)
        .build();
  }

  /** Scores are not computed: TopGroups.maxScore must be NaN. */
  public void testTopGroupsCollectorNoScoresReportsNaN() throws Exception {
    Query q = new TermQuery(new Term("body", "x"));
    FirstPassGroupingCollector<BytesRef> first =
        new FirstPassGroupingCollector<>(new TermGroupSelector("group"), BY_PRICE, 10);
    searcher.search(q, first);
    Collection<SearchGroup<BytesRef>> topGroups = first.getTopGroups(0);
    TopGroupsCollector<BytesRef> second =
        new TopGroupsCollector<>(
            new TermGroupSelector("group"), topGroups, BY_PRICE, BY_PRICE, 10, false);
    searcher.search(q, second);
    TopGroups<BytesRef> result = second.getTopGroups(0);
    assertEquals(2, result.groups.length);
    for (GroupDocs<BytesRef> gd : result.groups) {
      assertTrue("group maxScore " + gd.maxScore(), Float.isNaN(gd.maxScore()));
    }
    assertTrue(
        "TopGroups.maxScore should be NaN when scores were not computed, got " + result.maxScore,
        Float.isNaN(result.maxScore));
  }

  /** Every hit scores 0: the highest score across all hits is 0. */
  public void testGroupingSearchAllZeroScoresReportsZero() throws Exception {
    GroupingSearch gs = new GroupingSearch("group");
    gs.setGroupSort(BY_PRICE);
    gs.setSortWithinGroup(BY_PRICE);
    gs.setIncludeMaxScore(true);
    TopGroups<BytesRef> result = gs.search(searcher, zeroScoreQuery(), 0, 10);
    assertEquals(2, result.groups.length);
    for (GroupDocs<BytesRef> gd : result.groups) {
      for (var sd : gd.scoreDocs()) {
        assertTrue(Float.isNaN(sd.score) || sd.score == 0f);
      }
      assertEquals("GroupDocs.maxScore", 0f, gd.maxScore(), 0f);
    }
    assertEquals("TopGroups.maxScore", 0f, result.maxScore, 0f);
  }

  /** Block grouping, every hit scores 0: the highest score across all hits is 0. */
  public void testBlockGroupingAllZeroScoresReportsZero() throws Exception {
    GroupingSearch gs = new GroupingSearch(new TermQuery(new Term("groupend", "true")));
    gs.setGroupSort(BY_PRICE);
    gs.setSortWithinGroup(Sort.RELEVANCE);
    TopGroups<?> result = gs.search(searcher, zeroScoreQuery(), 0, 10);
    assertEquals(2, result.groups.length);
    for (GroupDocs<?> gd : result.groups) {
      assertEquals("GroupDocs.maxScore", 0f, gd.maxScore(), 0f);
    }
    assertEquals("TopGroups.maxScore", 0f, result.maxScore, 0f);
  }
}
