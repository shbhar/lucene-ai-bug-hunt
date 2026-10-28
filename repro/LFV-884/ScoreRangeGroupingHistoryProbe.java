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

import java.util.Collection;
import java.util.TreeSet;
import java.util.function.Supplier;
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
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Version-neutral history probe (Lucene 8.6 to main; public API only, no test framework).
 *
 * <p>Five documents with tags {a}, {a,b}, {a,b,c}, {a}, {a,b,c}; the query a OR b OR c of
 * constant-score clauses scores them 1, 2, 3, 1, 3. DoubleRangeGroupSelector and
 * LongRangeGroupSelector over DoubleValuesSource.SCORES with ranges of width 1 must give the three
 * groups [1,2), [2,3), [3,4) whatever the sort, and a second pass over those groups must place all
 * 5 hits. Checks: first pass with a relevance sort (control), first pass with a field sort, second
 * pass with a field sort and getMaxScores=false over the control's (correct) groups. Exit 1 if a
 * non-control check is wrong while the control is right; exit 2 if the control is wrong.
 */
public class ScoreRangeGroupingHistoryProbe {

  private static final String[][] TAGS = {{"a"}, {"a", "b"}, {"a", "b", "c"}, {"a"}, {"a", "b", "c"}};

  private static Query query() {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (String t : new String[] {"a", "b", "c"}) {
      b.add(new ConstantScoreQuery(new TermQuery(new Term("tag", t))), Occur.SHOULD);
    }
    return b.build();
  }

  private static <C extends Collector> void searchWith(IndexSearcher s, final C c)
      throws Exception {
    s.search(
        query(),
        new CollectorManager<C, Void>() {
          boolean created;

          @Override
          public C newCollector() {
            if (created) {
              throw new IllegalStateException("single slice expected");
            }
            created = true;
            return c;
          }

          @Override
          public Void reduce(Collection<C> cs) {
            return null;
          }
        });
  }

  private static <T> String values(Collection<SearchGroup<T>> groups) {
    TreeSet<String> out = new TreeSet<>();
    if (groups != null) {
      for (SearchGroup<T> g : groups) {
        out.add(String.valueOf(g.groupValue));
      }
    }
    return out.toString();
  }

  private static int bad;
  private static int controlBad;

  private static <T> void check(
      String kind, IndexSearcher s, Supplier<GroupSelector<T>> selectors, String expected)
      throws Exception {
    Sort byId = new Sort(new SortField("id", SortField.Type.INT));

    FirstPassGroupingCollector<T> control =
        new FirstPassGroupingCollector<>(selectors.get(), Sort.RELEVANCE, 10);
    searchWith(s, control);
    Collection<SearchGroup<T>> good = control.getTopGroups(0);
    String c = values(good);
    boolean cOk = expected.equals(c);
    System.out.println(
        kind + " firstPass(RELEVANCE) scoreMode=" + control.scoreMode() + " groups=" + c
            + (cOk ? " OK" : " WRONG (control)"));
    if (!cOk) {
      controlBad++;
    }

    FirstPassGroupingCollector<T> first =
        new FirstPassGroupingCollector<>(selectors.get(), byId, 10);
    searchWith(s, first);
    String f = values(first.getTopGroups(0));
    boolean fOk = expected.equals(f);
    System.out.println(
        kind + " firstPass(id) scoreMode=" + first.scoreMode() + " groups=" + f
            + (fOk ? " OK" : " WRONG expected " + expected));
    if (!fOk) {
      bad++;
    }

    if (cOk) {
      TopGroupsCollector<T> second =
          new TopGroupsCollector<>(selectors.get(), good, byId, byId, 10, false);
      searchWith(s, second);
      TopGroups<T> tg = second.getTopGroups(0);
      boolean sOk = tg.totalGroupedHitCount == TAGS.length;
      System.out.println(
          kind + " secondPass(id, getMaxScores=false) scoreMode=" + second.scoreMode()
              + " totalHitCount=" + tg.totalHitCount + " totalGroupedHitCount="
              + tg.totalGroupedHitCount + (sOk ? " OK" : " WRONG expected " + TAGS.length));
      if (!sOk) {
        bad++;
      }
    }
  }

  public static void main(String[] args) throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
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
        IndexSearcher s = new IndexSearcher(r);
        ScoreRangeGroupingHistoryProbe.<DoubleRange>check(
            "DoubleRangeGroupSelector(SCORES)",
            s,
            () -> new DoubleRangeGroupSelector(
                DoubleValuesSource.SCORES, new DoubleRangeFactory(0, 1, 10)),
            "[DoubleRange(1.0, 2.0), DoubleRange(2.0, 3.0), DoubleRange(3.0, 4.0)]");
        ScoreRangeGroupingHistoryProbe.<LongRange>check(
            "LongRangeGroupSelector(SCORES.toLongValuesSource())",
            s,
            () -> new LongRangeGroupSelector(
                DoubleValuesSource.SCORES.toLongValuesSource(), new LongRangeFactory(0, 1, 10)),
            "[LongRange(1, 2), LongRange(2, 3), LongRange(3, 4)]");
      }
    }
    if (controlBad > 0) {
      System.out.println("RESULT: PROBE-BROKEN control wrong in " + controlBad + " case(s)");
      System.exit(2);
    }
    if (bad > 0) {
      System.out.println("RESULT: BUG " + bad + " of 4 non-control checks wrong");
      System.exit(1);
    }
    System.out.println("RESULT: OK all checks right");
  }
}
