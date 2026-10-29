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
package org.apache.lucene.monitor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestParallelMatcherFailedMatch extends LuceneTestCase {

  public void testFailedMatchReleasesWorkers() throws Exception {
    Document doc = new Document();
    List<MonitorQuery> queries = new ArrayList<>();
    for (int i = 0; i <= IndexSearcher.getMaxClauseCount(); i++) {
      doc.add(new StringField("f" + i, "x", Field.Store.NO));
      queries.add(new MonitorQuery("q" + i, new TermQuery(new Term("f" + i, "x"))));
    }
    ExecutorService pool = Executors.newFixedThreadPool(1);
    try (Monitor monitor = new Monitor(new StandardAnalyzer())) {
      monitor.register(queries);
      expectThrows(
          IndexSearcher.TooManyClauses.class,
          () -> monitor.match(doc, ParallelMatcher.factory(pool, QueryMatch.SIMPLE_MATCHER, 1)));
      pool.shutdown();
      assertTrue(
          "expected the pool to terminate after the failed match, but its worker is still running",
          pool.awaitTermination(5, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }
  }
}
