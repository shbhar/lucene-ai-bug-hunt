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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestPartitionMatcherReportsEveryError extends LuceneTestCase {

  private static final class FailingQuery extends Query {
    @Override
    public Query rewrite(IndexSearcher searcher) throws IOException {
      throw new IOException("Error rewriting");
    }

    @Override
    public String toString(String field) {
      return "failing";
    }

    @Override
    public void visit(QueryVisitor visitor) {
      visitor.visitLeaf(this);
    }

    @Override
    public boolean equals(Object o) {
      return o == this;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  public void testEveryFailedQueryIsReported() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(8);
    try (Monitor monitor = new Monitor(new KeywordAnalyzer())) {
      List<MonitorQuery> queries = new ArrayList<>();
      Set<String> notReported = new TreeSet<>();
      for (int i = 0; i < 256; i++) {
        queries.add(new MonitorQuery("q" + i, new FailingQuery()));
        notReported.add("q" + i);
      }
      monitor.register(queries);
      MatchingQueries<QueryMatch> matches =
          monitor.match(
              new Document(), PartitionMatcher.factory(executor, QueryMatch.SIMPLE_MATCHER, 8));
      notReported.removeAll(matches.getErrors().keySet());
      // expected: every failed query is in getErrors(); actual: some of the 256 are missing
      assertEquals("failed queries missing from getErrors()", Set.of(), notReported);
    } finally {
      executor.shutdown();
    }
  }
}
