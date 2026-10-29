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
package org.apache.lucene.facet;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.NamedThreadFactory;

public class TestDrillSidewaysExecutorExceptionType extends LuceneTestCase {

  public void testExecutorThrowsSameExceptionAsSequential() throws Exception {
    Query failing =
        new Query() {
          @Override
          public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
              throws IOException {
            throw new IOException("simulated read failure");
          }

          @Override
          public String toString(String field) {
            return "failing";
          }

          @Override
          public void visit(QueryVisitor visitor) {}

          @Override
          public boolean equals(Object other) {
            return other == this;
          }

          @Override
          public int hashCode() {
            return System.identityHashCode(this);
          }
        };
    ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("ds"));
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(new Document());
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        FacetsConfig config = new FacetsConfig();
        DrillDownQuery query = new DrillDownQuery(config, failing);
        query.add("dim", "value");
        expectThrows(
            IOException.class,
            () -> new DrillSideways(searcher, config, null, null, null).search(query, 10));
        // Expected: the same IOException; actual: RuntimeException(ExecutionException(IOException)).
        expectThrows(
            IOException.class,
            () -> new DrillSideways(searcher, config, null, null, executor).search(query, 10));
      }
    } finally {
      executor.shutdown();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
  }
}
