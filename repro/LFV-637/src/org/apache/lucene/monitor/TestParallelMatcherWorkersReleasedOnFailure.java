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
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * ParallelMatcher submits its worker tasks when it is created, and only the end markers that
 * finish() puts on its internal queue ever stop them; each worker blocks in BlockingQueue#take
 * until then. So a worker that a legal executor runs on the calling thread (CallerRunsPolicy on a
 * saturated pool) blocks Monitor#match forever, and when the match fails before finish() (a
 * rejected submission, or an exception thrown inside Monitor#match) the submitted workers stay
 * blocked forever and keep their executor threads.
 */
public class TestParallelMatcherWorkersReleasedOnFailure extends LuceneTestCase {

  private static final int MANY_FIELDS = IndexSearcher.getMaxClauseCount() + 1;

  private static Monitor newMonitor(int numFields) throws Exception {
    Monitor monitor = new Monitor(new StandardAnalyzer());
    List<MonitorQuery> queries = new ArrayList<>();
    queries.add(new MonitorQuery("q", new TermQuery(new Term("body", "lucene"))));
    for (int i = 0; i < numFields; i++) {
      queries.add(new MonitorQuery("f" + i, new TermQuery(new Term("f" + i, "lucene"))));
    }
    monitor.register(queries);
    return monitor;
  }

  private static Document smallDoc() {
    Document doc = new Document();
    doc.add(new TextField("body", "lucene monitor", Field.Store.NO));
    return doc;
  }

  /**
   * A document with values in more fields than IndexSearcher.getMaxClauseCount() that registered
   * queries use: Monitor#match throws TooManyClauses from the presearcher query, after the
   * matcher has been created.
   */
  private static Document docWithManyFields() {
    Document doc = new Document();
    for (int i = 0; i < MANY_FIELDS; i++) {
      doc.add(new TextField("f" + i, "lucene", Field.Store.NO));
    }
    return doc;
  }

  /** Top frames of every thread that is inside ParallelMatcher (evidence for the failure message). */
  private static String parallelMatcherStacks() {
    StringBuilder sb = new StringBuilder();
    for (var e : Thread.getAllStackTraces().entrySet()) {
      StackTraceElement[] st = e.getValue();
      boolean inMatcher = false;
      for (StackTraceElement el : st) {
        if (el.getClassName().startsWith(ParallelMatcher.class.getName())) inMatcher = true;
      }
      if (inMatcher == false) continue;
      sb.append("\n  thread ").append(e.getKey().getName()).append(':');
      for (int i = 0; i < st.length && i < 14; i++) {
        String c = st[i].getClassName();
        if (c.startsWith("java.util.concurrent.locks")) continue;
        sb.append("\n    at ").append(st[i]);
      }
    }
    return sb.toString();
  }

  private static void assertTerminates(ExecutorService executor) throws InterruptedException {
    executor.shutdown();
    assertTrue(
        "executor still runs a worker of the failed match 10 s after shutdown()"
            + (executor.awaitTermination(10, TimeUnit.SECONDS) ? "" : parallelMatcherStacks()),
        executor.isTerminated());
  }

  /**
   * A saturated pool with the JDK's CallerRunsPolicy runs the rejected worker on the calling
   * thread, inside the ParallelMatcher constructor.
   */
  public void testCallerRunsPolicyPoolDoesNotHang() throws Exception {
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.CallerRunsPolicy());
    ExecutorService caller = Executors.newSingleThreadExecutor();
    try (Monitor monitor = newMonitor(0)) {
      Future<MatchingQueries<QueryMatch>> match =
          caller.submit(() -> monitor.match(smallDoc(), ParallelMatcher.factory(executor, QueryMatch.SIMPLE_MATCHER, 2)));
      MatchingQueries<QueryMatch> result;
      try {
        result = match.get(10, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        throw new AssertionError(
            "Monitor#match did not return within 10 s" + parallelMatcherStacks(), e);
      }
      assertEquals(1, result.getMatchCount());
    } finally {
      caller.shutdownNow();
      executor.shutdownNow();
      caller.awaitTermination(10, TimeUnit.SECONDS);
    }
  }

  /** A standard JDK pool (default AbortPolicy) that rejects the second submission. */
  public void testRejectedSubmitLeavesNoBlockedWorker() throws Exception {
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new SynchronousQueue<>());
    try (Monitor monitor = newMonitor(0)) {
      expectThrows(
          RejectedExecutionException.class,
          () -> monitor.match(smallDoc(), ParallelMatcher.factory(executor, QueryMatch.SIMPLE_MATCHER, 2)));
      assertTerminates(executor);
    } finally {
      executor.shutdownNow();
    }
  }

  /** Control: the default matcher throws the same exception for this document and leaks nothing. */
  public void testSerialMatcherThrowsTooManyClauses() throws Exception {
    try (Monitor monitor = newMonitor(MANY_FIELDS)) {
      expectThrows(
          IndexSearcher.TooManyClauses.class,
          () -> monitor.match(docWithManyFields(), QueryMatch.SIMPLE_MATCHER));
    }
  }

  /** Monitor#match fails after the ParallelMatcher was created: its workers must not stay. */
  public void testFailedMatchLeavesNoBlockedWorker() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try (Monitor monitor = newMonitor(MANY_FIELDS)) {
      expectThrows(
          IndexSearcher.TooManyClauses.class,
          () -> monitor.match(docWithManyFields(), ParallelMatcher.factory(executor, QueryMatch.SIMPLE_MATCHER, 2)));
      assertTerminates(executor);
    } finally {
      executor.shutdownNow();
    }
  }

  /** After one failed match, a valid match on the same executor must still return. */
  public void testMatchAfterFailedMatchReturns() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    ExecutorService caller = Executors.newSingleThreadExecutor();
    try (Monitor monitor = newMonitor(MANY_FIELDS)) {
      MatcherFactory<QueryMatch> factory =
          ParallelMatcher.factory(executor, QueryMatch.SIMPLE_MATCHER, 2);
      expectThrows(
          IndexSearcher.TooManyClauses.class, () -> monitor.match(docWithManyFields(), factory));

      Future<MatchingQueries<QueryMatch>> second =
          caller.submit(() -> monitor.match(smallDoc(), factory));
      MatchingQueries<QueryMatch> result;
      try {
        result = second.get(10, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        throw new AssertionError(
            "a valid Monitor#match on the same executor did not return within 10 s"
                + parallelMatcherStacks(),
            e);
      }
      assertEquals(1, result.getMatchCount());
    } finally {
      caller.shutdownNow();
      executor.shutdownNow();
      caller.awaitTermination(10, TimeUnit.SECONDS);
    }
  }
}
