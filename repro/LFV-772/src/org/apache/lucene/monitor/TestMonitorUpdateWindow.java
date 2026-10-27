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
 * A query that is being re-registered must still match, during the update, a document that both
 * its old and its new version match.
 *
 * <p>Only public API is used. The test parks the registering thread at the start of {@code
 * IndexWriter.commit()} through the query index's {@link InfoStream} (set with a {@link
 * MonitorConfiguration} subclass), so the interleaving is deterministic and no timing is involved.
 */
public class TestMonitorUpdateWindow extends LuceneTestCase {

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

  private static Query or(String... terms) {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (String t : terms) {
      b.add(new TermQuery(new Term("f", t)), BooleanClause.Occur.SHOULD);
    }
    return b.build();
  }

  private static Document doc(String text) {
    Document d = new Document();
    d.add(new TextField("f", text, Field.Store.NO));
    return d;
  }

  private static boolean matches(Monitor monitor, Document d, String id) throws IOException {
    return monitor.match(d, QueryMatch.SIMPLE_MATCHER).matches(id) != null;
  }

  /**
   * q is "apple OR banana" and is replaced by "cherry OR apple". Both versions match "apple", so
   * every match of "apple" must report q: before, during and after the update.
   */
  public void testReplacedQueryKeepsMatchingDuringUpdate() throws Exception {
    runUpdate(or("apple", "banana"), or("cherry", "apple"));
  }

  /** The same with a one-clause old version: "apple" is replaced by "cherry OR apple". */
  public void testAddingLeadingClauseDuringUpdate() throws Exception {
    runUpdate(or("apple"), or("cherry", "apple"));
  }

  private void runUpdate(Query oldVersion, Query newVersion) throws Exception {
    ParkAtCommit infoStream = new ParkAtCommit();
    MonitorConfiguration config =
        new MonitorConfiguration() {
          @Override
          protected IndexWriterConfig getIndexWriterConfig() {
            return super.getIndexWriterConfig().setInfoStream(infoStream);
          }
        };
    Document apple = doc("apple");
    try (Monitor monitor = new Monitor(new StandardAnalyzer(), config)) {
      monitor.register(new MonitorQuery("q", oldVersion));
      assertTrue("old version matches 'apple'", matches(monitor, apple, "q"));

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
      boolean duringUpdate;
      try {
        assertTrue(
            "updater reached IndexWriter.commit", infoStream.parked.await(30, TimeUnit.SECONDS));
        // register(newVersion) has not returned and has not committed anything yet
        duringUpdate = matches(monitor, apple, "q");
      } finally {
        infoStream.resume.countDown();
        updater.join();
      }
      assertNull("register failed: " + failure.get(), failure.get());
      boolean afterUpdate = matches(monitor, apple, "q");
      assertTrue("new version matches 'apple' after the update", afterUpdate);
      assertTrue(
          "a match that overlaps register() missed q although both its old version ("
              + oldVersion
              + ") and its new version ("
              + newVersion
              + ") match the document",
          duringUpdate);
    }
  }
}
