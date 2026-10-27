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

import java.util.Random;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.RamUsageTester;
import org.apache.lucene.tests.util.TestUtil;

/**
 * AutomatonQuery#ramBytesUsed() is computed once, in the constructor. Since GITHUB#15961 a
 * WildcardQuery (and since GITHUB#15939 a RegexpQuery) whose automaton is not deterministic runs
 * on an NFARunAutomaton that determinizes lazily and keeps every DFA state it builds, so the
 * query object grows while it executes. (The patterns below are small enough that 10.x, which
 * determinized up front with the default work limit, accepts them as DFAs; there the test passes.) TestAutomatonQuery asserts ramBytesUsed() within 10% of
 * RamUsageTester for a fresh query; after one search the reported value is orders of magnitude
 * too small, and LRUQueryCache budgets cached queries with this value.
 */
public class TestAutomatonQueryRamAfterNfaRun extends LuceneTestCase {

  private static Directory buildIndex() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec());
    Random r = new Random(42);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 20_000; i++) {
        char[] c = new char[20];
        for (int j = 0; j < c.length; j++) c[j] = r.nextBoolean() ? 'a' : 'b';
        Document d = new Document();
        d.add(new StringField("f", new String(c), Field.Store.NO));
        w.addDocument(d);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  public void testRamBytesUsedTracksLazilyBuiltStates() throws Exception {
    Directory dir = buildIndex();
    try (IndexReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setQueryCache(null);
      WildcardQuery q = new WildcardQuery(new Term("f", "*a????????????"));
      long reportedFresh = q.ramBytesUsed();
      long actualFresh = RamUsageTester.ramUsed(q);
      assertEquals("fresh query (as TestAutomatonQuery checks)", actualFresh, reportedFresh, actualFresh * 0.5);

      int hits = searcher.count(q);
      assertTrue(hits > 0);

      long reported = q.ramBytesUsed();
      long actual = RamUsageTester.ramUsed(q);
      assertTrue(
          "after one search: ramBytesUsed()="
              + reported
              + " but the query retains "
              + actual
              + " bytes (compiled automaton now reports "
              + q.getCompiled().ramBytesUsed()
              + ")",
          reported >= actual / 2);
    } finally {
      dir.close();
    }
  }

  /** The query cache budgets each distinct cached query with ramBytesUsed(). */
  public void testQueryCacheStaysWithinItsRamBudget() throws Exception {
    Directory dir = buildIndex();
    try (IndexReader reader = DirectoryReader.open(dir)) {
      long budget = 1 << 20; // 1 MB
      LRUQueryCache cache = new LRUQueryCache(1000, budget); // default leaf predicate / skip factor
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setQueryCache(cache);
      searcher.setQueryCachingPolicy(new UsageTrackingQueryCachingPolicy()); // the default policy
      String[] patterns = {"*a????????????", "*b????????????", "*a???????????b", "*b???????????a"};
      for (int round = 0; round < 3; round++) {
        for (String p : patterns) {
          // a fresh query object per request, as when a query string is parsed for every request
          searcher.search(new ConstantScoreQuery(new WildcardQuery(new Term("f", p))), 1);
        }
      }
      int cachedQueries = cache.cachedQueries().size();
      long accounted = cache.ramBytesUsed(); // queries + cached doc id sets, as the cache sees them
      // what the cache really keeps alive through its keys (package-private accessor, same package)
      long retainedByKeys = RamUsageTester.ramUsed(cache.cachedQueries());
      assertTrue(
          "LRUQueryCache(maxRamBytesUsed="
              + budget
              + ") accounts "
              + accounted
              + " bytes in total, but its "
              + cachedQueries
              + " cached query keys alone retain "
              + retainedByKeys
              + " bytes",
          retainedByKeys <= Math.max(budget, accounted));
    } finally {
      dir.close();
    }
  }
}
