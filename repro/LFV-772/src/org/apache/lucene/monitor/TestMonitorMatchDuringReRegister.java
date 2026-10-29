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

import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

public class TestMonitorMatchDuringReRegister extends LuceneTestCase {

  public void testMatchOnOtherThreadWhileReRegisterCommits() throws Exception {
    Document doc = new Document();
    doc.add(new TextField("f", "apple", Field.Store.NO));
    AtomicReference<Monitor> matchAtCommit = new AtomicReference<>();
    AtomicReference<Boolean> matchedDuringRegister = new AtomicReference<>();
    InfoStream infoStream =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            Monitor monitor =
                message.equals("commit: start") ? matchAtCommit.getAndSet(null) : null;
            if (monitor != null) {
              FutureTask<Boolean> match =
                  new FutureTask<>(
                      () -> monitor.match(doc, QueryMatch.SIMPLE_MATCHER).matches("q") != null);
              new Thread(match).start();
              try {
                matchedDuringRegister.set(match.get());
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return true;
          }

          @Override
          public void close() {}
        };
    MonitorConfiguration config =
        new MonitorConfiguration() {
          @Override
          protected IndexWriterConfig getIndexWriterConfig() {
            return super.getIndexWriterConfig().setInfoStream(infoStream);
          }
        };
    try (Monitor monitor = new Monitor(new StandardAnalyzer(), config)) {
      monitor.register(new MonitorQuery("q", or("apple", "banana")));
      matchAtCommit.set(monitor);
      monitor.register(new MonitorQuery("q", or("cherry", "apple")));
      // expected: a match while register() commits reports q (both versions match); actual: none
      assertEquals(
          "q matched 'apple' during re-register", Boolean.TRUE, matchedDuringRegister.get());
    }
  }

  private static Query or(String a, String b) {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("f", a)), Occur.SHOULD)
        .add(new TermQuery(new Term("f", b)), Occur.SHOULD)
        .build();
  }
}
