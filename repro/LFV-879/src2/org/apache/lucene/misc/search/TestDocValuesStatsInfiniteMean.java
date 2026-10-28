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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.misc.search.DocValuesStats.DoubleDocValuesStats;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Releases before 10.5 (no DocValuesStatsCollectorManager): the collector on its own. */
public class TestDocValuesStatsInfiniteMean extends LuceneTestCase {
  public void testMeanOfInfiniteAndFiniteValue() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (double v : new double[] {Double.POSITIVE_INFINITY, 1.0}) {
          Document doc = new Document();
          doc.add(new DoubleDocValuesField("price", v));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        DoubleDocValuesStats stats = new DoubleDocValuesStats("price");
        new IndexSearcher(reader).search(new MatchAllDocsQuery(), new DocValuesStatsCollector(stats));
        assertEquals(2, stats.count());
        assertEquals(Double.POSITIVE_INFINITY, stats.sum(), 0.0);
        assertEquals("mean", Double.POSITIVE_INFINITY, stats.mean(), 0.0);
      }
    }
  }
}
