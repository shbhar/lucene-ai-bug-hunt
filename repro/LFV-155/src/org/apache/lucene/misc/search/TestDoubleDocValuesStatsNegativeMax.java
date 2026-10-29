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

public class TestDoubleDocValuesStatsNegativeMax extends LuceneTestCase {

  public void testMaxOfSingleNegativeValue() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new DoubleDocValuesField("d", -1.0));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        DocValuesStatsCollectorManager<Double, DoubleDocValuesStats> manager =
            new DocValuesStatsCollectorManager<>(() -> new DoubleDocValuesStats("d"));
        DoubleDocValuesStats stats =
            new IndexSearcher(reader).search(MatchAllDocsQuery.INSTANCE, manager);
        // expected: max() == -1.0, the only value; actual: 4.9E-324 (Double.MIN_VALUE)
        assertEquals(-1.0, stats.max(), 0d);
      }
    }
  }
}
