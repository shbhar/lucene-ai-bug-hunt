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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import java.util.Collections;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Read-only variant: a read-only Monitor's Monitor.match must not fail while it refreshes
 * (purgeCache) to pick up queries that a writable Monitor registers in the same directory.
 *
 * <p>The Monitor keeps one term filter per open query-index reader. Matches that are still running
 * keep older readers open, so the number of open readers grows with slow concurrent matches and
 * frequent registrations. Here slow matches are simulated with a {@link CandidateMatcher} that waits
 * on a latch (public API), which keeps k older readers open while queries are registered; other
 * threads call Monitor.match at the same time. Only public API is used. The failure is a data race,
 * so it is probabilistic: the test repeats the scenario and reports how many matches failed.
 */
public class TestReadonlyMonitorTermFilterMapRace extends LuceneTestCase {

  private static Document doc(String text) {
    Document d = new Document();
    d.add(new TextField("f", text, Field.Store.NO));
    return d;
  }

  private static Query tq(String t) {
    return new TermQuery(new Term("f", t));
  }

  /** A matcher that blocks in its first matchQuery call until released: a slow match. */
  private static MatcherFactory<QueryMatch> slowMatcher(
      CountDownLatch entered, CountDownLatch release) {
    return (IndexSearcher searcher) ->
        new CandidateMatcher<QueryMatch>(searcher) {
          boolean first = true;

          @Override
          public void matchQuery(String queryId, Query matchQuery, Map<String, String> metadata)
              throws IOException {
            if (first) {
              first = false;
              entered.countDown();
              try {
                release.await();
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            }
          }

          @Override
          public QueryMatch resolve(QueryMatch match1, QueryMatch match2) {
            return match1;
          }
        };
  }

  private static Monitor openReadonly(Monitor writer, Directory readDir, MonitorQuerySerializer ser)
      throws IOException {
    writer.register(new MonitorQuery("q0", tq("apple"), "apple", Collections.emptyMap()));
    return new Monitor(
        new StandardAnalyzer(),
        new MonitorConfiguration()
            .setPurgeFrequency(1, java.util.concurrent.TimeUnit.DAYS)
            .setDirectoryProvider(() -> readDir, ser, true));
  }

  public void testReadonlyMatchWhileRefreshingWithSlowConcurrentMatches() throws Exception {
    int rounds = Integer.getInteger("tests.races.rounds", 200);
    int hammers = 4;
    // 12, 24 and 48 open older readers: the term-filter map grows past 12, 24 and 48 entries
    int maxHeld = 48;
    AtomicInteger failedMatches = new AtomicInteger();
    AtomicInteger matches = new AtomicInteger();
    AtomicReference<Throwable> firstFailure = new AtomicReference<>();
    Document slowDoc = doc("apple");
    Document fastDoc = doc("zzz");
    for (int round = 0; round < rounds && firstFailure.get() == null; round++) {
      CountDownLatch release = new CountDownLatch(1);
      List<Thread> slow = new ArrayList<>();
      MonitorQuerySerializer ser =
          MonitorQuerySerializer.fromParser(s -> new TermQuery(new Term("f", s)));
      Directory shared = new ByteBuffersDirectory();
      // the read-only Monitor does not close its directory; the writable one does
      Directory readDir = shared;
      try (Monitor writer =
              new Monitor(
                  new StandardAnalyzer(),
                  new MonitorConfiguration().setDirectoryProvider(() -> shared, ser));
          Monitor monitor = openReadonly(writer, readDir, ser)) {
       try {
        for (int held = 1; held <= maxHeld; held++) {
          // a slow match holds the current reader open ...
          CountDownLatch entered = new CountDownLatch(1);
          Thread t =
              new Thread(
                  () -> {
                    try {
                      monitor.match(slowDoc, slowMatcher(entered, release));
                    } catch (Throwable e) {
                      firstFailure.compareAndSet(null, e);
                      entered.countDown();
                    }
                  });
          t.start();
          slow.add(t);
          assertTrue(entered.await(30, TimeUnit.SECONDS));
          // ... while a registration opens a new reader; around the growth points other threads
          // match at the same time
          boolean growth = held == 12 || held == 24 || held == 48;
          List<Thread> fast = new ArrayList<>();
          CountDownLatch go = new CountDownLatch(1);
          AtomicInteger stop = new AtomicInteger();
          if (growth) {
            for (int h = 0; h < hammers; h++) {
              Thread f =
                  new Thread(
                      () -> {
                        try {
                          go.await();
                          while (stop.get() == 0) {
                            matches.incrementAndGet();
                            try {
                              monitor.match(fastDoc, QueryMatch.SIMPLE_MATCHER);
                            } catch (Throwable e) {
                              failedMatches.incrementAndGet();
                              firstFailure.compareAndSet(null, e);
                            }
                          }
                        } catch (InterruptedException e) {
                          throw new RuntimeException(e);
                        }
                      });
              f.start();
              fast.add(f);
            }
            go.countDown();
          }
          writer.register(
              new MonitorQuery("q" + held, tq("apple"), "apple", Collections.emptyMap()));
          monitor.purgeCache(); // the read-only Monitor opens the new reader
          stop.set(1);
          for (Thread f : fast) f.join();
        }
       } finally {
        // let the slow matches finish before the Monitor is closed
        release.countDown();
        for (Thread t : slow) t.join();
       }
      }
    }
    if (firstFailure.get() != null) {
      throw new AssertionError(
          "Monitor.match failed "
              + failedMatches.get()
              + " times in "
              + matches.get()
              + " concurrent matches; first failure:",
          firstFailure.get());
    }
  }
}
