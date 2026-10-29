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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.misc.search.DocValuesStats.LongDocValuesStats;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDocValuesStatsMergedMean extends LuceneTestCase {

  public void testMeanWithExecutor() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
        for (int i = 0; i < 6; i++) {
          Document doc = new Document();
          doc.add(new NumericDocValuesField("f", 2_000_000_000_000_000_000L));
          w.addDocument(doc);
          w.flush();
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        // 6 one-doc segments; with an executor the default slicing gives slices of 5 and 1
        LongDocValuesStats serial =
            new IndexSearcher(reader)
                .search(
                    MatchAllDocsQuery.INSTANCE,
                    new DocValuesStatsCollectorManager<>(() -> new LongDocValuesStats("f")));
        LongDocValuesStats withExecutor =
            new IndexSearcher(reader, Runnable::run)
                .search(
                    MatchAllDocsQuery.INSTANCE,
                    new DocValuesStatsCollectorManager<>(() -> new LongDocValuesStats("f")));
        assertEquals(serial.mean(), withExecutor.mean(), 1e-9 * serial.mean());
      }
    }
  }
}
