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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * PositiveScoresOnlyCollector is documented to wrap "any other Collector" and to make sure "only
 * documents with scores &gt; 0 are collected". It inherits scoreMode() and setWeight() from
 * FilterCollector, so (1) a wrapped collector that needs no scores makes the search produce
 * scorers without scores and every hit is dropped, and (2) a wrapped TotalHitCountCollector
 * counts whole segments with Weight#count and never goes through the score filter.
 *
 * <p>12 documents: every document has f:x; the 6 even documents also have g:y. Public API only.
 */
public class TestPositiveScoresOnlyCollectorDelegates extends LuceneTestCase {

  private static final Query ALL_ZERO = new BoostQuery(new TermQuery(new Term("f", "x")), 0f);

  /** g:y scores 2 * score(g:y) > 0 on the 6 even docs; f:x adds 0 on all 12 docs. */
  private static final Query MIXED =
      new BooleanQuery.Builder()
          .add(new BoostQuery(new TermQuery(new Term("g", "y")), 2f), Occur.SHOULD)
          .add(new BoostQuery(new TermQuery(new Term("f", "x")), 0f), Occur.SHOULD)
          .build();

  private static final Query ALL_POSITIVE = new TermQuery(new Term("g", "y"));

  private static Directory index(int numSegments) throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 12; i++) {
        Document doc = new Document();
        doc.add(new StringField("f", "x", Field.Store.NO));
        if (i % 2 == 0) {
          doc.add(new StringField("g", "y", Field.Store.NO));
        }
        doc.add(new NumericDocValuesField("sort", i));
        w.addDocument(doc);
        if (numSegments > 1 && i % (12 / numSegments) == 12 / numSegments - 1) {
          w.commit();
        }
      }
      w.commit();
    }
    return dir;
  }

  private static IndexSearcher searcher(DirectoryReader reader) {
    IndexSearcher searcher = new IndexSearcher(reader);
    // the query cache does not change the outcome; disabled so that the run does not depend on
    // the test framework's randomized default cache
    searcher.setQueryCache(null);
    return searcher;
  }

  /** Number of matches of {@code query} whose score is &gt; 0, with COMPLETE scores, no wrapper. */
  private static int positiveCount(IndexSearcher searcher, Query query) throws IOException {
    TopDocs td = searcher.search(query, new TopScoreDocCollectorManager(100, Integer.MAX_VALUE));
    int n = 0;
    for (ScoreDoc sd : td.scoreDocs) {
      if (sd.score > 0) {
        n++;
      }
    }
    return n;
  }

  /** PositiveScoresOnlyCollector(TotalHitCountCollector) through a CollectorManager. */
  private static int countPositive(IndexSearcher searcher, Query query) throws IOException {
    List<TotalHitCountCollector> counters = Collections.synchronizedList(new ArrayList<>());
    return searcher.search(
        query,
        new CollectorManager<PositiveScoresOnlyCollector, Integer>() {
          @Override
          public PositiveScoresOnlyCollector newCollector() {
            TotalHitCountCollector counter = new TotalHitCountCollector();
            counters.add(counter);
            return new PositiveScoresOnlyCollector(counter);
          }

          @Override
          public Integer reduce(Collection<PositiveScoresOnlyCollector> collectors) {
            int sum = 0;
            for (TotalHitCountCollector c : counters) {
              sum += c.getTotalHits();
            }
            return sum;
          }
        });
  }

  /** Hazard 2: the wrapped TotalHitCountCollector counts segments through Weight#count. */
  public void testWrappedTotalHitCountCollector() throws IOException {
    List<String> wrong = new ArrayList<>();
    for (int numSegments : new int[] {1, 3}) {
      try (Directory dir = index(numSegments);
          DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = searcher(reader);
        for (Query query : new Query[] {ALL_ZERO, MIXED}) {
          int expected = positiveCount(searcher, query); // 0 for ALL_ZERO, 6 for MIXED
          int actual = countPositive(searcher, query);
          if (actual != expected) {
            wrong.add(
                "segments=" + reader.leaves().size() + " query=" + query + " expected=" + expected
                    + " actual=" + actual);
          }
        }
      }
    }
    assertTrue("PositiveScoresOnlyCollector(TotalHitCountCollector) counts: " + wrong, wrong.isEmpty());
  }

  /**
   * Hazard 1: a wrapped collector that needs no scores (sort by a field) makes the search produce
   * scorers without scores, and the filter then reads those unavailable scores.
   */
  public void testWrappedSortByFieldCollector() throws IOException {
    List<String> wrong = new ArrayList<>();
    for (int numSegments : new int[] {1, 3}) {
      try (Directory dir = index(numSegments);
          DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = searcher(reader);
        for (Query query : new Query[] {ALL_ZERO, MIXED, ALL_POSITIVE}) {
          int expected = positiveCount(searcher, query); // 0, 6 and 6
          // the collector behind IndexSearcher#search(Query, int, Sort): it sorts by a field and
          // needs no scores (ScoreMode.TOP_DOCS)
          TopFieldCollectorManager sortByField =
              new TopFieldCollectorManager(
                  new Sort(new SortField("sort", SortField.Type.LONG)), 100, 1000);
          List<TopFieldCollector> made = Collections.synchronizedList(new ArrayList<>());
          TopDocs td =
              searcher.search(
                  query,
                  new CollectorManager<PositiveScoresOnlyCollector, TopDocs>() {
                    @Override
                    public PositiveScoresOnlyCollector newCollector() throws IOException {
                      TopFieldCollector c = sortByField.newCollector();
                      made.add(c);
                      return new PositiveScoresOnlyCollector(c);
                    }

                    @Override
                    public TopDocs reduce(Collection<PositiveScoresOnlyCollector> collectors)
                        throws IOException {
                      return sortByField.reduce(made);
                    }
                  });
          if (td.scoreDocs.length != expected) {
            wrong.add(
                "segments=" + reader.leaves().size() + " query=" + query + " sorted by field:"
                    + " expected=" + expected + " hits, actual=" + td.scoreDocs.length);
          }
        }
      }
    }
    assertTrue("PositiveScoresOnlyCollector(TopFieldCollector) hits: " + wrong, wrong.isEmpty());
  }
}
