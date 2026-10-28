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

import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.misc.search.DocValuesStats.DoubleDocValuesStats;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DoubleDocValuesStats.mean() is "the mean of all values of the field". For the values {+Infinity,
 * 1.0} that mean is +Infinity (what DoubleSummaryStatistics, the upstream test's oracle, returns),
 * and it must not depend on whether the searcher has an executor.
 */
public class TestDocValuesStatsInfiniteMean extends LuceneTestCase {

  private static final double[] VALUES = {Double.POSITIVE_INFINITY, 1.0};

  /** Executor searcher with one segment per slice, so reduce() merges two partial stats. */
  private static final class OneSegmentPerSlice extends IndexSearcher {
    OneSegmentPerSlice(IndexReader r, ExecutorService ex) {
      super(r, ex);
    }

    @Override
    protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
      return slices(leaves, 1, 1, false);
    }
  }

  private Directory buildIndex() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (double v : VALUES) {
        Document doc = new Document();
        doc.add(new DoubleDocValuesField("price", v));
        w.addDocument(doc);
        w.flush(); // one segment per value
      }
    }
    return dir;
  }

  public void testMeanOfInfiniteAndFiniteValue() throws Exception {
    DoubleSummaryStatistics expected = new DoubleSummaryStatistics();
    for (double v : VALUES) {
      expected.accept(v);
    }
    assertEquals(Double.POSITIVE_INFINITY, expected.getAverage(), 0.0);
    try (Directory dir = buildIndex();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      DoubleDocValuesStats serial =
          new IndexSearcher(reader)
              .search(
                  MatchAllDocsQuery.INSTANCE,
                  new DocValuesStatsCollectorManager<>(() -> new DoubleDocValuesStats("price")));
      assertEquals(2, serial.count());
      assertEquals(Double.POSITIVE_INFINITY, serial.max(), 0.0);
      assertEquals(Double.POSITIVE_INFINITY, serial.sum(), 0.0);
      assertEquals("serial mean", expected.getAverage(), serial.mean(), 0.0);
    }
  }

  public void testMeanDoesNotDependOnTheExecutor() throws Exception {
    try (Directory dir = buildIndex();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      DoubleDocValuesStats serial =
          new IndexSearcher(reader)
              .search(
                  MatchAllDocsQuery.INSTANCE,
                  new DocValuesStatsCollectorManager<>(() -> new DoubleDocValuesStats("price")));
      ExecutorService executor = Executors.newFixedThreadPool(2);
      try {
        DoubleDocValuesStats concurrent =
            new OneSegmentPerSlice(reader, executor)
                .search(
                    MatchAllDocsQuery.INSTANCE,
                    new DocValuesStatsCollectorManager<>(() -> new DoubleDocValuesStats("price")));
        assertEquals(
            "serial mean " + serial.mean() + " vs concurrent mean " + concurrent.mean(),
            Double.doubleToLongBits(concurrent.mean()),
            Double.doubleToLongBits(serial.mean()));
      } finally {
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
      }
    }
  }
}
