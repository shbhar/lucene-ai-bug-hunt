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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import org.apache.lucene.util.InfoStream;

/**
 * main() variant of TestMonitorUpdateWindow without the test framework, for old releases. Exits 1
 * when a match that overlaps register() misses q, although both versions of q match the document.
 */
public class MonitorUpdateWindowMain {
  static volatile Thread target;
  static final CountDownLatch parked = new CountDownLatch(1);
  static final CountDownLatch resume = new CountDownLatch(1);

  static Query or(String... terms) {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (String t : terms) b.add(new TermQuery(new Term("f", t)), BooleanClause.Occur.SHOULD);
    return b.build();
  }

  public static void main(String[] args) throws Exception {
    InfoStream info =
        new InfoStream() {
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
        };
    MonitorConfiguration config =
        new MonitorConfiguration() {
          @Override
          protected IndexWriterConfig getIndexWriterConfig() {
            IndexWriterConfig iwc = super.getIndexWriterConfig();
            iwc.setInfoStream(info);
            return iwc;
          }
        };
    // q = "apple" is replaced by "<x> OR apple". The Monitor gives each disjunct the cache id
    // q_<position in decompose()>; pick an x whose disjunct comes first, so that "apple" moves from
    // q_0 to q_1 (on releases whose decomposer orders disjuncts by hash, the position depends on x).
    String x = null;
    for (String cand : new String[] {"cherry", "banana", "kiwi", "lemon", "mango", "pear", "plum", "fig"}) {
      Query first = new QueryDecomposer().decompose(or(cand, "apple")).iterator().next();
      if (first.equals(new TermQuery(new Term("f", cand)))) {
        x = cand;
        break;
      }
    }
    if (x == null) throw new IllegalStateException("no candidate term");
    final String newTerm = x;
    Document apple = new Document();
    apple.add(new TextField("f", "apple", Field.Store.NO));
    boolean during;
    boolean before;
    boolean after;
    try (Monitor monitor = new Monitor(new StandardAnalyzer(), config)) {
      monitor.register(new MonitorQuery("q", or("apple")));
      before = monitor.match(apple, QueryMatch.SIMPLE_MATCHER).matches("q") != null;
      Thread updater =
          new Thread(
              () -> {
                try {
                  monitor.register(new MonitorQuery("q", or(newTerm, "apple")));
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      target = updater;
      updater.start();
      if (!parked.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("not parked");
      during = monitor.match(apple, QueryMatch.SIMPLE_MATCHER).matches("q") != null;
      resume.countDown();
      updater.join();
      after = monitor.match(apple, QueryMatch.SIMPLE_MATCHER).matches("q") != null;
    }
 System.out.println("q: 'apple' -> '" + newTerm + " OR apple'");
    System.out.println("q matches 'apple': before=" + before + " during=" + during + " after=" + after);
    if (!before || !after) throw new IllegalStateException("setup broken");
    if (!during) {
      System.out.println("BUG: match during register() missed q; both versions match 'apple'");
      System.exit(1);
    }
    System.out.println("OK");
  }
}
