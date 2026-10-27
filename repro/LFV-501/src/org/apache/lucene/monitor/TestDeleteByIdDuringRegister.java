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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Monitor.deleteById(id) that runs while register() is indexing the decomposed parts of the same
 * id leaves only some of the parts in the query index. The query is still registered (getQuery,
 * getQueryCount) but silently misses documents that only match the removed parts.
 *
 * <p>The latch only pins the interleaving: the presearcher adds an empty extra field whose token
 * stream waits while the second disjunct's document is being indexed. Public API only.
 */
public class TestDeleteByIdDuringRegister extends LuceneTestCase {

  /** Delegates to TermFilteredPresearcher; adds an empty field that can pause indexing. */
  static final class PausingPresearcher extends Presearcher {
    final Presearcher in = new TermFilteredPresearcher();
    final CountDownLatch paused = new CountDownLatch(1);
    final CountDownLatch resume = new CountDownLatch(1);
    volatile Query pauseOn;

    @Override
    public Query buildQuery(LeafReader reader, BiPredicate<String, BytesRef> termAcceptor) {
      return in.buildQuery(reader, termAcceptor);
    }

    @Override
    public Document indexQuery(Query query, Map<String, String> metadata) {
      Document doc = in.indexQuery(query, metadata);
      final boolean pause = query.equals(pauseOn);
      doc.add(
          new Field(
              "pause_hook",
              new TokenStream() {
                boolean done;

                @Override
                public boolean incrementToken() {
                  if (pause && done == false) {
                    done = true;
                    paused.countDown();
                    try {
                      resume.await();
                    } catch (InterruptedException e) {
                      throw new RuntimeException(e);
                    }
                  }
                  return false;
                }
              },
              TextField.TYPE_NOT_STORED));
      return doc;
    }
  }

  private static Query term(String t) {
    return new TermQuery(new Term("f", t));
  }

  private static Document doc(String text) {
    Document d = new Document();
    d.add(new TextField("f", text, Field.Store.NO));
    return d;
  }

  public void testDeleteByIdDuringRegisterLeavesPartialQuery() throws Exception {
    PausingPresearcher presearcher = new PausingPresearcher();
    try (Monitor monitor = new Monitor(new WhitespaceAnalyzer(), presearcher)) {
      // decomposes into three parts: q_0 = f:a, q_1 = f:b, q_2 = f:c
      Query q =
          new BooleanQuery.Builder()
              .add(term("a"), BooleanClause.Occur.SHOULD)
              .add(term("b"), BooleanClause.Occur.SHOULD)
              .add(term("c"), BooleanClause.Occur.SHOULD)
              .build();
      presearcher.pauseOn = term("b");
      AtomicReference<Throwable> error = new AtomicReference<>();
      Thread register =
          new Thread(
              () -> {
                try {
                  monitor.register(new MonitorQuery("q", q));
                } catch (Throwable t) {
                  error.set(t);
                }
              });
      register.start();
      presearcher.paused.await(); // register() has indexed f:a and is indexing f:b
      Thread delete =
          new Thread(
              () -> {
                try {
                  monitor.deleteById("q");
                } catch (Throwable t) {
                  error.set(t);
                }
              });
      delete.start();
      // deleteById has issued its IndexWriter delete and now waits for register()'s commit
      while (delete.getState() != Thread.State.BLOCKED && delete.isAlive()) {
        Thread.yield();
      }
      presearcher.resume.countDown();
      register.join();
      delete.join();
      assertNull(error.get());
      presearcher.pauseOn = null;

      // Whatever order the two calls took effect in, "q" must be either absent or complete.
      boolean registered = monitor.getQueryCount() == 1;
      if (registered) {
        for (String t : new String[] {"a", "b", "c"}) {
          assertEquals(
              "q is registered (getQueryIds="
                  + monitor.getQueryIds()
                  + ", getDisjunctCount="
                  + monitor.getDisjunctCount()
                  + ") but does not match a document with '"
                  + t
                  + "'",
              1,
              monitor.match(doc(t), QueryMatch.SIMPLE_MATCHER).getMatchCount());
        }
      } else {
        assertEquals(0, monitor.getDisjunctCount());
      }
    }
  }
}
