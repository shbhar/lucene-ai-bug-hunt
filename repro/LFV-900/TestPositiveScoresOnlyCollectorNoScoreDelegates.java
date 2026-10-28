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
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOSupplier;

/**
 * PositiveScoresOnlyCollector "wraps any other Collector and prevents collection of hits whose
 * score is &lt;= 0.0". Wrapped around a collector that needs no scores, it gets the hits wrong.
 *
 * <p>12 documents: all have f:x, the 6 even ones also have g:y. Every g:y match scores &gt; 0.
 */
public class TestPositiveScoresOnlyCollectorNoScoreDelegates extends LuceneTestCase {

  /** 6 matches, all with a score &gt; 0. */
  private static final Query POSITIVE = new TermQuery(new Term("g", "y"));

  /** 12 matches, all with a score of 0. */
  private static final Query ZERO = new BoostQuery(new TermQuery(new Term("f", "x")), 0f);

  /** 12 matches: the 6 g:y documents score &gt; 0, the other 6 score 0. */
  private static final Query MIXED =
      new BooleanQuery.Builder()
          .add(new BoostQuery(POSITIVE, 2f), Occur.SHOULD)
          .add(ZERO, Occur.SHOULD)
          .build();

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 12; i++) {
        Document doc = new Document();
        doc.add(new StringField("f", "x", Field.Store.NO));
        if (i % 2 == 0) {
          doc.add(new StringField("g", "y", Field.Store.NO));
        }
        doc.add(new NumericDocValuesField("price", i));
        w.addDocument(doc);
      }
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
    searcher.setQueryCache(null);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  /** Searches with a PositiveScoresOnlyCollector around each new collector; returns those. */
  private <C extends Collector> List<C> searchWrapped(Query query, IOSupplier<C> newCollector)
      throws IOException {
    List<C> wrapped = Collections.synchronizedList(new ArrayList<>());
    searcher.search(
        query,
        new CollectorManager<PositiveScoresOnlyCollector, Void>() {
          @Override
          public PositiveScoresOnlyCollector newCollector() throws IOException {
            C c = newCollector.get();
            wrapped.add(c);
            return new PositiveScoresOnlyCollector(c);
          }

          @Override
          public Void reduce(Collection<PositiveScoresOnlyCollector> collectors) {
            return null;
          }
        });
    return wrapped;
  }

  /** Hits that score > 0, with a collector that needs scores: the documented use, which works. */
  private int hitsWithScoringCollector(Query query) throws IOException {
    TopScoreDocCollectorManager m = new TopScoreDocCollectorManager(100, Integer.MAX_VALUE);
    return m.reduce(searchWrapped(query, m::newCollector)).scoreDocs.length;
  }

  /** The same, sorted by a field: the collector behind IndexSearcher#search(Query, int, Sort). */
  private int hitsSortedByField(Query query) throws IOException {
    Sort byPrice = new Sort(new SortField("price", SortField.Type.LONG));
    TopFieldCollectorManager m = new TopFieldCollectorManager(byPrice, 100, 1000);
    return m.reduce(searchWrapped(query, m::newCollector)).scoreDocs.length;
  }

  /** The same, only counted. */
  private int count(Query query) throws IOException {
    int total = 0;
    for (TotalHitCountCollector c : searchWrapped(query, TotalHitCountCollector::new)) {
      total += c.getTotalHits();
    }
    return total;
  }

  public void testSortByFieldCollectorGetsThePositiveHits() throws IOException {
    assertEquals("scoring collector", 6, hitsWithScoringCollector(POSITIVE));
    assertEquals("sort-by-field collector, query " + POSITIVE, 6, hitsSortedByField(POSITIVE));
  }

  public void testCountOfZeroScoreQuery() throws IOException {
    assertEquals("scoring collector", 0, hitsWithScoringCollector(ZERO));
    assertEquals("TotalHitCountCollector, query " + ZERO, 0, count(ZERO));
  }

  public void testCountOfMixedQuery() throws IOException {
    assertEquals("scoring collector", 6, hitsWithScoringCollector(MIXED));
    assertEquals("TotalHitCountCollector, query " + MIXED, 6, count(MIXED));
  }
}
