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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMonitorMatchDuringRegister extends LuceneTestCase {

  public void testMatchWhileRegistering() throws Exception {
    Document doc = new Document();
    doc.add(new TextField("f", "a", Field.Store.NO));
    AtomicReference<Throwable> failure = new AtomicReference<>();
    for (int round = 0; round < 100 && failure.get() == null; round++) {
      try (Monitor monitor = new Monitor(new StandardAnalyzer())) {
        monitor.register(new MonitorQuery("a", new TermQuery(new Term("f", "a"))));
        AtomicBoolean stop = new AtomicBoolean();
        Thread matcher =
            new Thread(
                () -> {
                  while (stop.get() == false) {
                    try {
                      monitor.match(doc, QueryMatch.SIMPLE_MATCHER);
                    } catch (Throwable t) {
                      failure.compareAndSet(null, t);
                    }
                  }
                });
        matcher.start();
        try {
          registerWhileMatching(monitor, doc, 48);
        } finally {
          stop.set(true);
          matcher.join();
        }
      }
    }
    // expected: match never throws while a query is registered; actual: NullPointerException
    if (failure.get() != null) {
      throw new AssertionError("Monitor.match threw while a query was registered", failure.get());
    }
  }

  private static void registerWhileMatching(Monitor monitor, Document doc, int depth)
      throws IOException {
    if (depth == 0) {
      return;
    }
    monitor.match(
        doc,
        searcher ->
            new CandidateMatcher<QueryMatch>(searcher) {
              @Override
              public void matchQuery(String queryId, Query matchQuery, Map<String, String> metadata)
                  throws IOException {
                Query q = new TermQuery(new Term("f", "q" + depth));
                monitor.register(new MonitorQuery("q" + depth, q));
                registerWhileMatching(monitor, doc, depth - 1);
              }

              @Override
              public QueryMatch resolve(QueryMatch match1, QueryMatch match2) {
                return match1;
              }
            });
  }
}
