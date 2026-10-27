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

import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * No hooks: stock Monitor with the default presearcher and decomposer. Two threads, released by a
 * barrier: one registers q (a disjunction of PARTS terms), the other deletes q (or clears) after a
 * random busy-wait. Afterwards q must have 0 or PARTS indexed parts; anything in between is a state
 * no serial order of the two calls can produce. Counts such outcomes over a bounded run.
 */
public class TestDeleteDuringRegisterStress extends LuceneTestCase {

  static final int PARTS = 64;
  static final int MAX_ITERS = 400;
  static final long BUDGET_NS = TimeUnit.SECONDS.toNanos(20);

  static Query wideDisjunction() {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (int i = 0; i < PARTS; i++) {
      b.add(new TermQuery(new Term("f", "t" + i)), BooleanClause.Occur.SHOULD);
    }
    return b.build();
  }

  static void spin(long nanos) {
    long end = System.nanoTime() + nanos;
    while (System.nanoTime() < end) {
      Thread.onSpinWait();
    }
  }

  private void run(boolean useClear) throws Exception {
    Random r = new Random(random().nextLong());
    int partial = 0, iters = 0;
    String firstPartial = null;
    long start = System.nanoTime();
    try (Monitor monitor = new Monitor(new KeywordAnalyzer())) {
      Query q = wideDisjunction();
      for (; iters < MAX_ITERS && System.nanoTime() - start < BUDGET_NS; iters++) {
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicReference<Throwable> err = new AtomicReference<>();
        long delay = (long) (r.nextDouble() * 3_000_000L); // 0..3 ms
        Thread registrar =
            new Thread(
                () -> {
                  try {
                    barrier.await();
                    monitor.register(new MonitorQuery("q", q));
                  } catch (Throwable t) {
                    err.set(t);
                  }
                });
        Thread deleter =
            new Thread(
                () -> {
                  try {
                    barrier.await();
                    spin(delay);
                    if (useClear) monitor.clear();
                    else monitor.deleteById("q");
                  } catch (Throwable t) {
                    err.set(t);
                  }
                });
        registrar.start();
        deleter.start();
        registrar.join();
        deleter.join();
        if (err.get() != null) throw new AssertionError(err.get());
        int parts = monitor.getDisjunctCount();
        if (parts != 0 && parts != PARTS) {
          partial++;
          if (firstPartial == null) {
            firstPartial =
                "iteration "
                    + iters
                    + ": getQueryIds="
                    + monitor.getQueryIds()
                    + " getDisjunctCount="
                    + parts;
          }
        }
        monitor.clear(); // serial reset
        assertEquals(0, monitor.getDisjunctCount());
      }
    }
    String summary =
        (useClear ? "clear()" : "deleteById(q)")
            + " vs register(q with "
            + PARTS
            + " parts): partial outcomes "
            + partial
            + "/"
            + iters
            + (firstPartial == null ? "" : ", first: " + firstPartial);
    System.out.println(summary);
    assertEquals(summary, 0, partial);
  }

  public void testDeleteById() throws Exception {
    run(false);
  }

  public void testClear() throws Exception {
    run(true);
  }
}
