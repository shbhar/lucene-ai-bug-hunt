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
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.function.BiPredicate;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestMonitorDeleteByIdDuringRegister extends LuceneTestCase {

  private Monitor monitor;
  private Thread delete;

  private void deleteWhileIndexing() {
    if (delete == null) {
      delete =
          new Thread(
              () -> {
                try {
                  monitor.deleteById("q");
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
      delete.start();
      while (delete.getState() != Thread.State.BLOCKED && delete.isAlive()) {
        Thread.yield();
      }
    }
  }

  public void testDeleteByIdDuringRegisterLeavesQueryPartlyIndexed() throws Exception {
    Query b = new TermQuery(new Term("f", "b"));
    Presearcher presearcher =
        new Presearcher() {
          final Presearcher in = new TermFilteredPresearcher();

          @Override
          public Query buildQuery(LeafReader reader, BiPredicate<String, BytesRef> termAcceptor) {
            return in.buildQuery(reader, termAcceptor);
          }

          @Override
          public Document indexQuery(Query query, Map<String, String> metadata) {
            Document doc = in.indexQuery(query, metadata);
            if (query.equals(b)) {
              TokenStream hook =
                  new TokenStream() {
                    @Override
                    public boolean incrementToken() {
                      deleteWhileIndexing();
                      return false;
                    }
                  };
              doc.add(new Field("hook", hook, TextField.TYPE_NOT_STORED));
            }
            return doc;
          }
        };
    Query q =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("f", "a")), Occur.SHOULD)
            .add(b, Occur.SHOULD)
            .add(new TermQuery(new Term("f", "c")), Occur.SHOULD)
            .build();
    Document docA = new Document();
    docA.add(new TextField("f", "a", Field.Store.NO));
    try (Monitor m = new Monitor(new StandardAnalyzer(), presearcher)) {
      monitor = m;
      monitor.register(new MonitorQuery("q", q));
      delete.join();
      // expected: q matches docA iff q is still registered; actual: registered q misses docA
      assertEquals(
          "matches of docA, getQueryIds=" + monitor.getQueryIds(),
          monitor.getQueryCount(),
          monitor.match(docA, QueryMatch.SIMPLE_MATCHER).getMatchCount());
    }
  }
}
