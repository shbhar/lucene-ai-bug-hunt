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
package org.apache.lucene.search;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.QueryTimeout;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndexSearcher#count on a two-term disjunction uses inclusion-exclusion, count(a) + count(b) -
 * count(a AND b). The two term counts come from index statistics and never check the timeout, but
 * the intersection is counted by collecting hits, which the timeout cuts short. The subtraction of
 * a partial intersection count then makes the timed-out count LARGER than the true count (and
 * possibly larger than maxDoc), and a later timeout gives a SMALLER count than an earlier one.
 *
 * <p>Deterministic: one segment, NoMergePolicy, a plain IndexSearcher without executor or query
 * cache, and a QueryTimeout that answers false for the first {@code allowed} shouldExit() calls and
 * true afterwards (a deadline that has passed stays passed).
 */
public class TestTimedOutCountTwoTermDisjunction extends LuceneTestCase {

  private static QueryTimeout expiresAfter(int allowed) {
    AtomicInteger calls = new AtomicInteger();
    return () -> calls.getAndIncrement() >= allowed;
  }

  private static Directory index(int maxDoc, IntPredicate hasA, IntPredicate hasB)
      throws Exception {
    Directory dir = newDirectory();
    try (IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      for (int i = 0; i < maxDoc; i++) {
        Document d = new Document();
        if (hasA.test(i)) d.add(new StringField("f", "a", Field.Store.NO));
        if (hasB.test(i)) d.add(new StringField("f", "b", Field.Store.NO));
        w.addDocument(d);
      }
    }
    return dir;
  }

  private static Query aOrB() {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("f", "a")), BooleanClause.Occur.SHOULD)
        .add(new TermQuery(new Term("f", "b")), BooleanClause.Occur.SHOULD)
        .build();
  }

  private static IndexSearcher searcher(DirectoryReader r, QueryTimeout timeout) {
    IndexSearcher s = new IndexSearcher(r);
    s.setQueryCache(null);
    s.setTimeout(timeout);
    return s;
  }

  /** a in 900 of 1000 docs, b in 60 docs (50 of them also have a): 910 docs match a OR b. */
  public void testTimedOutCountIsNotALowerBound() throws Exception {
    try (Directory dir = index(1000, i -> i % 10 != 9, i -> i % 20 == 0 || i % 100 == 99);
        DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals(1, r.leaves().size());
      final int trueCount = 910;
      assertEquals(trueCount, searcher(r, null).count(aOrB()));

      // The documented alternative to count(): it collects, so a timeout gives a lower bound.
      IndexSearcher viaCollector = searcher(r, expiresAfter(0));
      int collected = viaCollector.search(aOrB(), new TotalHitCountCollectorManager(viaCollector.getSlices()));
      assertTrue(viaCollector.timedOut());
      assertTrue("collector count " + collected, collected <= trueCount);

      StringBuilder trace = new StringBuilder();
      int previous = -1;
      boolean monotone = true;
      int maxSeen = 0;
      for (int allowed = 0; allowed <= 5; allowed++) {
        IndexSearcher s = searcher(r, expiresAfter(allowed));
        int count = s.count(aOrB());
        trace.append(" allowed=").append(allowed).append(":count=").append(count)
            .append(",timedOut=").append(s.timedOut());
        if (previous != -1 && count < previous) monotone = false;
        previous = count;
        maxSeen = Math.max(maxSeen, count);
      }
      assertTrue(
          "a timed-out count() must not exceed the number of matching documents ("
              + trueCount + ");" + trace,
          maxSeen <= trueCount);
      assertTrue("a later timeout must not give a smaller count;" + trace, monotone);
    }
  }

  /** a in 950 of 1000 docs, b in 91 docs (86 also have a): 955 match, count(a)+count(b) = 1041. */
  public void testTimedOutCountExceedsMaxDoc() throws Exception {
    try (Directory dir = index(1000, i -> i % 20 != 19, i -> i % 11 == 0);
        DirectoryReader r = DirectoryReader.open(dir)) {
      final int trueCount = 955;
      assertEquals(trueCount, searcher(r, null).count(aOrB()));
      IndexSearcher s = searcher(r, expiresAfter(0)); // deadline already passed
      int count = s.count(aOrB());
      assertTrue(s.timedOut());
      assertTrue(
          "timed-out count() = " + count + " > maxDoc = " + r.maxDoc() + " (true count " + trueCount + ")",
          count <= r.maxDoc());
    }
  }
}
