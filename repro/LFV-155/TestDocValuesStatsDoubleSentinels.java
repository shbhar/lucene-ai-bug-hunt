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
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DoubleDocValuesStats and SortedDoubleDocValuesStats start max at Double.MIN_VALUE, the smallest
 * POSITIVE double (4.9E-324), and min at Double.MAX_VALUE. max() is therefore wrong whenever every
 * value is {@code <= 0} (for example all-negative or all-zero data), and min() is wrong when every
 * value is +Infinity. Public API only (DocValuesStatsCollector, DocValuesStats).
 */
@SuppressWarnings("deprecation")
public class TestDocValuesStatsDoubleSentinels extends LuceneTestCase {

  private static final double[] VALUES = {-5.0, -3.0, -4.0};

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (double v : VALUES) {
        Document doc = new Document();
        doc.add(new DoubleDocValuesField("d", v));
        doc.add(new SortedNumericDocValuesField("sd", Double.doubleToRawLongBits(v)));
        doc.add(new DoubleDocValuesField("zero", 0.0));
        doc.add(new DoubleDocValuesField("inf", Double.POSITIVE_INFINITY));
        w.addDocument(doc);
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

  private DocValuesStats.DoubleDocValuesStats stats(String field) throws Exception {
    DocValuesStats.DoubleDocValuesStats stats = new DocValuesStats.DoubleDocValuesStats(field);
    searcher.search(new MatchAllDocsQuery(), new DocValuesStatsCollector(stats));
    assertEquals(VALUES.length, stats.count());
    return stats;
  }

  public void testNegativeValuesMax() throws Exception {
    DocValuesStats.DoubleDocValuesStats stats = stats("d");
    assertEquals(-5.0, stats.min(), 0d);
    assertEquals(-3.0, stats.max(), 0d);
  }

  public void testAllZeroMax() throws Exception {
    assertEquals(0.0, stats("zero").max(), 0d);
  }

  public void testAllPositiveInfinityMin() throws Exception {
    assertEquals(Double.POSITIVE_INFINITY, stats("inf").min(), 0d);
  }

  public void testSortedNumericNegativeValuesMax() throws Exception {
    DocValuesStats.SortedDoubleDocValuesStats stats =
        new DocValuesStats.SortedDoubleDocValuesStats("sd");
    searcher.search(new MatchAllDocsQuery(), new DocValuesStatsCollector(stats));
    assertEquals(VALUES.length, stats.count());
    assertEquals(-5.0, stats.min(), 0d);
    assertEquals(-3.0, stats.max(), 0d);
  }
}
