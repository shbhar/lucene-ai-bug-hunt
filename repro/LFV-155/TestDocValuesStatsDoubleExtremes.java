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
package org.apache.lucene.misc.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.misc.search.DocValuesStats.DoubleDocValuesStats;
import org.apache.lucene.misc.search.DocValuesStats.LongDocValuesStats;
import org.apache.lucene.misc.search.DocValuesStats.SortedDoubleDocValuesStats;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * min() and max() of the double DocValuesStats must be the smallest and largest collected value
 * whenever count() is greater than zero, also when the values are zero or negative.
 */
public class TestDocValuesStatsDoubleExtremes extends LuceneTestCase {

  /** The same index gives the right max for all documents and a wrong one for the winter ones. */
  public void testMaxOfFilteredNegativeValues() throws Exception {
    try (Directory dir = newDirectory();
        RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
      for (double t : new double[] {21.0, 18.5}) {
        Document doc = new Document();
        doc.add(new StringField("season", "summer", Store.NO));
        doc.add(new DoubleDocValuesField("temp", t));
        w.addDocument(doc);
      }
      for (double t : new double[] {-12.5, -3.0, -7.25}) {
        Document doc = new Document();
        doc.add(new StringField("season", "winter", Store.NO));
        doc.add(new DoubleDocValuesField("temp", t));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = w.getReader()) {
        IndexSearcher searcher = newSearcher(reader);

        DoubleDocValuesStats all =
            searcher.search(
                MatchAllDocsQuery.INSTANCE,
                new DocValuesStatsCollectorManager<>(() -> new DoubleDocValuesStats("temp")));
        assertEquals(5, all.count());
        assertEquals(-12.5, all.min(), 0d);
        assertEquals(21.0, all.max(), 0d);

        DoubleDocValuesStats winter =
            searcher.search(
                new TermQuery(new Term("season", "winter")),
                new DocValuesStatsCollectorManager<>(() -> new DoubleDocValuesStats("temp")));
        assertEquals(3, winter.count());
        assertEquals(-22.75, winter.sum(), 0d);
        assertEquals(-12.5, winter.min(), 0d);
        assertEquals("max of {-12.5, -3.0, -7.25}", -3.0, winter.max(), 0d);
      }
    }
  }

  /** With a single value, min, max, sum and mean are all that value. */
  public void testMaxOfSingleNegativeValue() throws Exception {
    try (Directory dir = newDirectory();
        RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
      Document doc = new Document();
      doc.add(new DoubleDocValuesField("balance", -2.0));
      w.addDocument(doc);
      try (DirectoryReader reader = w.getReader()) {
        IndexSearcher searcher = newSearcher(reader);
        DoubleDocValuesStats stats =
            searcher.search(
                MatchAllDocsQuery.INSTANCE,
                new DocValuesStatsCollectorManager<>(() -> new DoubleDocValuesStats("balance")));
        assertEquals(1, stats.count());
        assertEquals(-2.0, stats.sum(), 0d);
        assertEquals(-2.0, stats.mean(), 0d);
        assertEquals(-2.0, stats.min(), 0d);
        assertEquals("max of {-2.0}", -2.0, stats.max(), 0d);
      }
    }
  }

  /** A field that is 0.0 in every document: stdev is 0, so max must equal min. */
  public void testMaxOfAllZeros() throws Exception {
    try (Directory dir = newDirectory();
        RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
      for (int i = 0; i < 4; i++) {
        Document doc = new Document();
        doc.add(new DoubleDocValuesField("discount", 0.0));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = w.getReader()) {
        IndexSearcher searcher = newSearcher(reader);
        DoubleDocValuesStats stats =
            searcher.search(
                MatchAllDocsQuery.INSTANCE,
                new DocValuesStatsCollectorManager<>(() -> new DoubleDocValuesStats("discount")));
        assertEquals(4, stats.count());
        assertEquals(0.0, stats.stdev(), 0d);
        assertEquals(0.0, stats.min(), 0d);
        assertEquals("max of {0.0, 0.0, 0.0, 0.0}", 0.0, stats.max(), 0d);
      }
    }
  }

  /** Multi-valued doubles, encoded as raw bits like TestDocValuesStatsCollector does. */
  public void testSortedMaxOfNegativeValues() throws Exception {
    try (Directory dir = newDirectory();
        RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
      double[][] perDoc = {{-1.5, -8.0}, {-0.5}, {-4.0, -2.0, -6.0}};
      for (double[] values : perDoc) {
        Document doc = new Document();
        for (double v : values) {
          doc.add(new SortedNumericDocValuesField("sd", Double.doubleToRawLongBits(v)));
        }
        w.addDocument(doc);
      }
      try (DirectoryReader reader = w.getReader()) {
        IndexSearcher searcher = newSearcher(reader);
        SortedDoubleDocValuesStats stats =
            searcher.search(
                MatchAllDocsQuery.INSTANCE,
                new DocValuesStatsCollectorManager<>(() -> new SortedDoubleDocValuesStats("sd")));
        assertEquals(3, stats.count());
        assertEquals(6, stats.valuesCount());
        assertEquals(-8.0, stats.min(), 0d);
        assertEquals("max of {-1.5, -8.0, -0.5, -4.0, -2.0, -6.0}", -0.5, stats.max(), 0d);
      }
    }
  }

  /** The mirror case on the min side (a corner case). */
  public void testMinOfAllPositiveInfinity() throws Exception {
    try (Directory dir = newDirectory();
        RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
      for (int i = 0; i < 2; i++) {
        Document doc = new Document();
        doc.add(new DoubleDocValuesField("d", Double.POSITIVE_INFINITY));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = w.getReader()) {
        IndexSearcher searcher = newSearcher(reader);
        DoubleDocValuesStats stats =
            searcher.search(
                MatchAllDocsQuery.INSTANCE,
                new DocValuesStatsCollectorManager<>(() -> new DoubleDocValuesStats("d")));
        assertEquals(2, stats.count());
        assertEquals(Double.POSITIVE_INFINITY, stats.max(), 0d);
        assertEquals("min of {+Inf, +Inf}", Double.POSITIVE_INFINITY, stats.min(), 0d);
      }
    }
  }

  /** Control: the long variant starts at Long.MIN_VALUE and is right on negative values. */
  public void testLongMaxOfNegativeValues() throws Exception {
    try (Directory dir = newDirectory();
        RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
      for (long v : new long[] {-5, -3, -4}) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("n", v));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = w.getReader()) {
        IndexSearcher searcher = newSearcher(reader);
        LongDocValuesStats stats =
            searcher.search(
                MatchAllDocsQuery.INSTANCE,
                new DocValuesStatsCollectorManager<>(() -> new LongDocValuesStats("n")));
        assertEquals(3, stats.count());
        assertEquals(-5L, stats.min().longValue());
        assertEquals(-3L, stats.max().longValue());
      }
    }
  }
}
