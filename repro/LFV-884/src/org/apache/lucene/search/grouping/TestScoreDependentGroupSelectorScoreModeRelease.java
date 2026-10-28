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

import java.util.Map;
import java.util.TreeMap;
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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Grouping by ranges of a score-dependent DoubleValuesSource (here DoubleValuesSource.SCORES).
 *
 * <p>Five documents with tags {a}, {a,b}, {a,b,c}, {a}, {a,b,c}; the query a OR b OR c of
 * constant-score clauses scores them 1, 2, 3, 1, 3. Grouped into ranges of width 1 the groups are
 * [1,2) = {0, 3}, [2,3) = {1}, [3,4) = {2, 4}, whatever the group sort.
 *
 * <p>Release variant (8.6-10.5 API: GroupingSearch takes a GroupSelector instance, no
 * AllGroupsCollectorManager). A plain IndexSearcher is used, so the failure is what production code sees (no test-framework
 * assertion is involved).
 */
public class TestScoreDependentGroupSelectorScoreModeRelease extends LuceneTestCase {

  private static final String[][] TAGS = {{"a"}, {"a", "b"}, {"a", "b", "c"}, {"a"}, {"a", "b", "c"}};

  private static final String EXPECTED =
      "{DoubleRange(1.0, 2.0)=[0, 3], DoubleRange(2.0, 3.0)=[1], DoubleRange(3.0, 4.0)=[2, 4]}";

  private static Query query() {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (String t : new String[] {"a", "b", "c"}) {
      b.add(new ConstantScoreQuery(new TermQuery(new Term("tag", t))), Occur.SHOULD);
    }
    return b.build();
  }

  private static DoubleRangeGroupSelector scoreRanges() {
    return new DoubleRangeGroupSelector(
        DoubleValuesSource.SCORES, new DoubleRangeFactory(0, 1, 10));
  }

  private interface Body {
    void run(IndexSearcher searcher) throws Exception;
  }

  private void withSearcher(Body body) throws Exception {
    try (Directory dir = newDirectory()) {
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
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        body.run(new IndexSearcher(r));
      }
    }
  }

  private static String groups(TopGroups<DoubleRange> tg) {
    Map<String, java.util.List<Integer>> m = new TreeMap<>();
    for (GroupDocs<DoubleRange> gd : tg.groups) {
      java.util.List<Integer> docs = new java.util.ArrayList<>();
      for (ScoreDoc sd : gd.scoreDocs()) {
        docs.add(sd.doc);
      }
      java.util.Collections.sort(docs);
      m.put(String.valueOf(gd.groupValue()), docs);
    }
    return m.toString();
  }

  private static TopGroups<DoubleRange> search(IndexSearcher s, Sort sort) throws Exception {
    GroupingSearch g = new GroupingSearch(scoreRanges());
    g.setGroupSort(sort);
    g.setSortWithinGroup(sort);
    g.setGroupDocsLimit(10);
    g.setAllGroups(true);
    return g.search(s, query(), 0, 10);
  }

  /** Control: with relevance group sort the collectors ask for scores and the groups are right. */
  public void testRelevanceGroupSortControl() throws Exception {
    withSearcher(
        s -> {
          TopGroups<DoubleRange> tg = search(s, Sort.RELEVANCE);
          assertEquals(EXPECTED, groups(tg));
          assertEquals(Integer.valueOf(3), tg.totalGroupCount);
        });
  }

  /** The same grouping with a group sort that does not need scores. */
  public void testFieldGroupSort() throws Exception {
    withSearcher(
        s -> {
          TopGroups<DoubleRange> tg = search(s, new Sort(new SortField("id", SortField.Type.INT)));
          assertEquals(EXPECTED, groups(tg));
          assertEquals(Integer.valueOf(3), tg.totalGroupCount);
        });
  }

  /** AllGroupsCollector on its own: the distinct score ranges of the matching documents. */
  public void testAllGroupsCollector() throws Exception {
    withSearcher(
        s -> {
          AllGroupsCollector<DoubleRange> c = new AllGroupsCollector<>(scoreRanges());
          s.search(
              query(),
              new org.apache.lucene.search.CollectorManager<AllGroupsCollector<DoubleRange>, Void>() {
                boolean created;

                @Override
                public AllGroupsCollector<DoubleRange> newCollector() {
                  assertFalse("single slice expected", created);
                  created = true;
                  return c;
                }

                @Override
                public Void reduce(java.util.Collection<AllGroupsCollector<DoubleRange>> cs) {
                  return null;
                }
              });
          java.util.Collection<DoubleRange> all = c.getGroups();
          java.util.Set<String> got = new java.util.TreeSet<>();
          for (DoubleRange dr : all) {
            got.add(String.valueOf(dr));
          }
          assertEquals(
              "[DoubleRange(1.0, 2.0), DoubleRange(2.0, 3.0), DoubleRange(3.0, 4.0)]",
              got.toString());
        });
  }
}
