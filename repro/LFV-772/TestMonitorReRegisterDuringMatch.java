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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

/**
 * A query that is being re-registered must keep matching, while register() runs, a document that
 * both its old and its new version match.
 *
 * <p>Public API only. The registering thread is parked when the query index's IndexWriter starts
 * its commit, through an {@link InfoStream} set with a {@link MonitorConfiguration} subclass, so
 * the interleaving is deterministic and no sleeps are involved.
 */
public class TestMonitorReRegisterDuringMatch extends LuceneTestCase {

  /** Parks one chosen thread when the query index's IndexWriter starts a commit. */
  static final class ParkAtCommit extends InfoStream {
    volatile Thread target;
    final CountDownLatch parked = new CountDownLatch(1);
    final CountDownLatch resume = new CountDownLatch(1);

    @Override
    public void message(String component, String message) {
      if (Thread.currentThread() == target && message.equals("commit: start")) {
        target = null;
        parked.countDown();
        try {
          resume.await();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }

    @Override
    public boolean isEnabled(String component) {
      return "IW".equals(component);
    }

    @Override
    public void close() {}
  }

  private static TermQuery term(String t) {
    return new TermQuery(new Term("f", t));
  }

  private static Query or(String... terms) {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (String t : terms) {
      b.add(term(t), BooleanClause.Occur.SHOULD);
    }
    return b.build();
  }

  /** Position of the clause for {@code t} in the decomposition, which is the N of cache id q_N. */
  private static int position(Query q, String t) {
    List<Query> parts = new ArrayList<>(new QueryDecomposer().decompose(q));
    return parts.indexOf(term(t));
  }

  private static boolean matchesQ(Monitor monitor) throws IOException {
    Document apple = new Document();
    apple.add(new TextField("f", "apple", Field.Store.NO));
    return monitor.match(apple, QueryMatch.SIMPLE_MATCHER).matches("q") != null;
  }

  /** "apple OR banana" becomes "cherry OR apple": apple moves from q_0 to q_1. */
  public void testReorderedDisjunctionKeepsMatchingDuringRegister() throws Exception {
    assertMatchesThroughout(or("apple", "banana"), or("cherry", "apple"), true);
  }

  /** "apple" becomes "cherry OR apple": a new leading clause moves apple from q_0 to q_1. */
  public void testNewLeadingClauseKeepsMatchingDuringRegister() throws Exception {
    assertMatchesThroughout(or("apple"), or("cherry", "apple"), true);
  }

  /** Control: "apple OR banana" becomes "apple OR cherry": apple stays at q_0. */
  public void testControlClauseKeepsItsPosition() throws Exception {
    assertMatchesThroughout(or("apple", "banana"), or("apple", "cherry"), false);
  }

  private void assertMatchesThroughout(Query oldVersion, Query newVersion, boolean appleMoves)
      throws Exception {
    assertEquals(
        "precondition: does apple's cache position change?",
        appleMoves,
        position(oldVersion, "apple") != position(newVersion, "apple"));
    ParkAtCommit infoStream = new ParkAtCommit();
    MonitorConfiguration config =
        new MonitorConfiguration() {
          @Override
          protected IndexWriterConfig getIndexWriterConfig() {
            return super.getIndexWriterConfig().setInfoStream(infoStream);
          }
        };
    try (Monitor monitor = new Monitor(new StandardAnalyzer(), config)) {
      monitor.register(new MonitorQuery("q", oldVersion));
      boolean before = matchesQ(monitor);

      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread updater =
          new Thread(
              () -> {
                try {
                  monitor.register(new MonitorQuery("q", newVersion));
                } catch (Throwable t) {
                  failure.set(t);
                }
              },
              "updater");
      infoStream.target = updater;
      updater.start();
      boolean during;
      try {
        assertTrue(
            "updater reached IndexWriter.commit", infoStream.parked.await(30, TimeUnit.SECONDS));
        // register(newVersion) is inside its commit: it has not returned yet
        during = matchesQ(monitor);
      } finally {
        infoStream.resume.countDown();
        updater.join();
      }
      assertNull("register failed: " + failure.get(), failure.get());
      boolean after = matchesQ(monitor);
      String trace =
          oldVersion
              + " -> "
              + newVersion
              + ": before="
              + before
              + " during="
              + during
              + " after="
              + after;
      assertTrue("old version must match 'apple': " + trace, before);
      assertTrue("new version must match 'apple': " + trace, after);
      assertTrue(
          "a match during register() missed q although both versions match 'apple': " + trace,
          during);
    }
  }
}
