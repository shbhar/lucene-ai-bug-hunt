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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.NamedThreadFactory;

/**
 * When running a candidate query throws, the Monitor reports the exception for that query and still
 * returns the other matches (Monitor's collector catches Exception and calls
 * CandidateMatcher.reportError, "Called by the Monitor if running a query throws an Exception";
 * TestMonitorErrorHandling). ParallelMatcher and PartitionMatcher run the queries on worker threads
 * that only catch IOException. A RuntimeException, for example IndexSearcher.TooManyClauses from a
 * legal query with more than maxClauseCount leaf clauses, escapes the worker: PartitionMatcher and
 * ParallelMatcher then make Monitor.match throw away every result, and ParallelMatcher hangs forever
 * once the dead workers stop draining its bounded task queue.
 *
 * <p>Uses only public API.
 */
public class TestConcurrentMatcherQueryErrors extends LuceneTestCase {

  private static final String FIELD = "text";

  /** A legal query whose rewrite throws IndexSearcher.TooManyClauses (a RuntimeException). */
  private static Query tooManyClauses() {
    int half = IndexSearcher.getMaxClauseCount() / 2 + 1;
    BooleanQuery.Builder b1 = new BooleanQuery.Builder();
    BooleanQuery.Builder b2 = new BooleanQuery.Builder();
    for (int i = 0; i < half; i++) {
      b1.add(new TermQuery(new Term(FIELD, "t" + i)), Occur.MUST);
      b2.add(new TermQuery(new Term(FIELD, "t" + (half + i))), Occur.MUST);
    }
    return new BooleanQuery.Builder().add(b1.build(), Occur.MUST).add(b2.build(), Occur.MUST).build();
  }

  private static Document doc() {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < 2 * (IndexSearcher.getMaxClauseCount() / 2 + 1); i++) {
      text.append('t').append(i).append(' ');
    }
    Document doc = new Document();
    doc.add(new TextField(FIELD, text.toString(), Field.Store.NO));
    return doc;
  }

  private static Monitor monitor(int goodQueries) throws IOException {
    Monitor monitor = new Monitor(new WhitespaceAnalyzer());
    List<MonitorQuery> queries = new ArrayList<>();
    queries.add(new MonitorQuery("bad", tooManyClauses()));
    for (int i = 0; i < goodQueries; i++) {
      queries.add(new MonitorQuery("good" + i, new TermQuery(new Term(FIELD, "t0"))));
    }
    monitor.register(queries);
    return monitor;
  }

  private static void assertPerQueryError(MatchingQueries<QueryMatch> matches, int goodQueries) {
    assertEquals(goodQueries, matches.getMatchCount());
    assertEquals(1, matches.getErrors().size());
    assertTrue(matches.getErrors().get("bad") instanceof IndexSearcher.TooManyClauses);
  }

  public void testSerialMatcherReportsTheError() throws IOException {
    try (Monitor monitor = monitor(10)) {
      assertPerQueryError(monitor.match(doc(), QueryMatch.SIMPLE_MATCHER), 10);
    }
  }

  public void testPartitionMatcherReportsTheError() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2, new NamedThreadFactory("matchers"));
    try (Monitor monitor = monitor(10)) {
      assertPerQueryError(
          monitor.match(doc(), PartitionMatcher.factory(executor, QueryMatch.SIMPLE_MATCHER, 2)), 10);
    } finally {
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
    }
  }

  public void testParallelMatcherReportsTheError() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2, new NamedThreadFactory("matchers"));
    try (Monitor monitor = monitor(10)) {
      assertPerQueryError(
          monitor.match(doc(), ParallelMatcher.factory(executor, QueryMatch.SIMPLE_MATCHER, 1)), 10);
    } finally {
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
    }
  }

  public void testParallelMatcherDoesNotHang() throws Exception {
    // 1 worker thread; more candidates after the failing one than the matcher's task queue holds (1024)
    int goodQueries = 1100;
    ExecutorService executor = Executors.newFixedThreadPool(1, new NamedThreadFactory("matchers"));
    try (Monitor monitor = monitor(goodQueries)) {
      AtomicReference<Object> outcome = new AtomicReference<>();
      Thread matchThread =
          new Thread(
              () -> {
                try {
                  outcome.set(
                      monitor.match(doc(), ParallelMatcher.factory(executor, QueryMatch.SIMPLE_MATCHER, 1)));
                } catch (Throwable t) {
                  outcome.set(t);
                }
              },
              "monitor-match");
      matchThread.start();
      matchThread.join(TimeUnit.SECONDS.toMillis(60));
      boolean hung = matchThread.isAlive();
      int interrupts = 0;
      // Cleanup only. One interrupt unblocks one queue.put; the Monitor records the resulting
      // IOException("Interrupted during match") as an error of that query and blocks again on the
      // next candidate, so it takes one interrupt per remaining candidate to stop the thread.
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
      while (matchThread.isAlive() && System.nanoTime() < deadline) {
        matchThread.interrupt();
        interrupts++;
        matchThread.join(5);
      }
      assertFalse(
          "Monitor.match with ParallelMatcher did not return within 60 seconds (it took "
              + interrupts
              + " interrupts to stop the matching thread)",
          hung);
      if (outcome.get() instanceof Throwable t) {
        throw new AssertionError("Monitor.match threw instead of reporting a per-query error", t);
      }
      @SuppressWarnings("unchecked")
      MatchingQueries<QueryMatch> matches = (MatchingQueries<QueryMatch>) outcome.get();
      assertPerQueryError(matches, goodQueries);
    } finally {
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
    }
  }
}
