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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Unhooked version: one thread keeps re-registering id q, alternating between two versions that
 * both match the document; the test thread keeps matching. No hook, no parking: any miss is a
 * naturally scheduled occurrence. The matching disjunct is at a different position in the two
 * versions (checked with the decomposer).
 */
public class TestRegisterMatchStressV1 extends LuceneTestCase {

  private static final String F = "f";
  private static final int ROUNDS = 400;

  private static TermQuery tq(String term) {
    return new TermQuery(new Term(F, term));
  }

  private static Query disj(String a, String b) {
    return new BooleanQuery.Builder()
        .add(tq(a), BooleanClause.Occur.SHOULD)
        .add(tq(b), BooleanClause.Occur.SHOULD)
        .build();
  }

  public void testConcurrentReRegisterNeverMissesUnhooked() throws Exception {
    runStress(true);
  }

  /** Control: the matching disjunct keeps its position (cache id) in both versions; must pass. */
  public void testControlSamePositionUnhooked() throws Exception {
    runStress(false);
  }

  private void runStress(boolean movePosition) throws Exception {
    String[] fillers = {"banana", "cherry", "date", "elder", "fig", "grape", "kiwi", "lemon"};
    Query a = null;
    Query b = null;
    outer:
    for (String x : fillers) {
      for (String y : fillers) {
        if (x.equals(y)) continue;
        Query qa = disj("apple", x);
        Query qb = movePosition ? disj(y, "apple") : disj("apple", y);
        List<Query> da = new ArrayList<>(new QueryDecomposer().decompose(qa));
        List<Query> db = new ArrayList<>(new QueryDecomposer().decompose(qb));
        if (da.size() == 2
            && db.size() == 2
            && (da.indexOf(tq("apple")) != db.indexOf(tq("apple"))) == movePosition) {
          a = qa;
          b = qb;
          break outer;
        }
      }
    }
    assertNotNull(a);
    final Query[] versions = {a, b};

    try (Monitor monitor = new Monitor(new StandardAnalyzer())) {
      monitor.register(new MonitorQuery("q", versions[0]));
      AtomicBoolean done = new AtomicBoolean();
      AtomicReference<Throwable> regError = new AtomicReference<>();
      Thread registerer =
          new Thread(
              () -> {
                try {
                  for (int i = 1; i <= ROUNDS; i++) {
                    monitor.register(new MonitorQuery("q", versions[i % 2]));
                  }
                } catch (Throwable t) {
                  regError.set(t);
                } finally {
                  done.set(true);
                }
              },
              "registerer");
      registerer.start();
      long matches = 0;
      long misses = 0;
      long errors = 0;
      while (done.get() == false) {
        Document doc = new Document();
        doc.add(new TextField(F, "apple", Field.Store.NO));
        MatchingQueries<QueryMatch> m = monitor.match(doc, QueryMatch.SIMPLE_MATCHER);
        matches++;
        if (m.getErrors().isEmpty() == false) errors++;
        if (m.matches("q") == null) misses++;
      }
      registerer.join(60_000);
      if (regError.get() != null) throw new AssertionError(regError.get());
      System.out.println(
          (movePosition ? "unhooked" : "unhooked-control")
              + ": versions="
              + new QueryDecomposer().decompose(versions[0])
              + " / "
              + new QueryDecomposer().decompose(versions[1])
              + " registers="
              + ROUNDS
              + " matches="
              + matches
              + " misses="
              + misses
              + " errors="
              + errors);
      assertEquals("match errors", 0, errors);
      assertEquals(
          "matches concurrent with re-registration missed q (both versions match) in "
              + misses
              + " of "
              + matches
              + " matches",
          0,
          misses);
    } catch (IOException e) {
      throw e;
    }
  }
}
