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
package org.apache.lucene.facet.range;

import static org.apache.lucene.facet.range.RangeFastMatchSecurityFixture.*;

import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.UsageTrackingQueryCachingPolicy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Security probe: the fast-match query of a range drill-down carries an access-style restriction
 * (an allow flag or a deny flag stored in doc values). Request A fills a shared cache. The
 * application then changes the flag in place and reopens. Request B has a different base query and
 * was never run before; it carries the same range drill-down. Every test asserts that B's cached
 * answer equals the uncached answer on the same reader, so on an affected build every test FAILS
 * and the message shows both sets.
 */
public class TestRangeFastMatchSecurityProbe extends LuceneTestCase {

  private static Query requestA(Query fastMatch) {
    return drillDown(new MatchAllDocsQuery(), fastMatch);
  }

  private static Query requestB(Query fastMatch) {
    return drillDown(new TermQuery(new Term("category", "shoes")), fastMatch);
  }

  private interface Mutation {
    void apply(IndexWriter w) throws Exception;
  }

  private static SortedSet<Integer>[] run(
      Query fastMatch, Mutation mutation, QueryCachingPolicy policy, int repeatsOfA)
      throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        addDocs(w, 0, NUM_DOCS);
        w.commit();
        LRUQueryCache cache = newCache();
        try (DirectoryReader r1 = DirectoryReader.open(w)) {
          SortedSet<Integer> before = null;
          for (int k = 0; k < repeatsOfA; k++) {
            before = ids(cached(r1, cache, policy), requestA(fastMatch));
          }
          mutation.apply(w);
          try (DirectoryReader r2 = DirectoryReader.openIfChanged(r1, w)) {
            assertNotNull(r2);
            SortedSet<Integer> expected = ids(uncached(r2), requestB(fastMatch));
            SortedSet<Integer> actual = ids(cached(r2, cache, policy), requestB(fastMatch));
            @SuppressWarnings({"unchecked", "rawtypes"})
            SortedSet<Integer>[] out = new SortedSet[] {before, expected, actual};
            return out;
          }
        }
      }
    }
  }

  private static String report(String name, SortedSet<Integer>[] r) {
    SortedSet<Integer> extra = new TreeSet<>(r[2]);
    extra.removeAll(r[1]);
    SortedSet<Integer> missing = new TreeSet<>(r[1]);
    missing.removeAll(r[2]);
    String line =
        name
            + ": requestA(before update)="
            + r[0]
            + " requestB uncached="
            + r[1]
            + " requestB cached="
            + r[2]
            + " over-matched="
            + extra
            + " missing="
            + missing;
    System.out.println(line);
    return line;
  }

  /** Allow flag: revoke doc 0 (visible 1 -> 0) and grant doc 15 (visible 0 -> 1). */
  public void testAllowFlagRevokeAndGrant() throws Exception {
    SortedSet<Integer>[] r =
        run(
            allowFastMatch(),
            w -> {
              w.updateNumericDocValue(new Term("id", "0"), "visible", 0);
              w.updateNumericDocValue(new Term("id", "15"), "visible", 1);
            },
            ALWAYS_CACHE,
            1);
    String line = report("allow-flag revoke 0 / grant 15, always-cache", r);
    assertEquals(line, r[1], r[2]);
  }

  /** Deny flag: block doc 3 (blocked 0 -> 1). */
  public void testDenyFlagBlock() throws Exception {
    SortedSet<Integer>[] r =
        run(
            denyFastMatch(),
            w -> w.updateNumericDocValue(new Term("id", "3"), "blocked", 1),
            ALWAYS_CACHE,
            1);
    String line = report("deny-flag block 3, always-cache", r);
    assertEquals(line, r[1], r[2]);
  }

  /** Allow flag with the stock caching policy; request A is repeated so the policy caches it. */
  public void testAllowFlagRevokeDefaultPolicy() throws Exception {
    SortedSet<Integer>[] r =
        run(
            allowFastMatch(),
            w -> w.updateNumericDocValue(new Term("id", "0"), "visible", 0),
            new UsageTrackingQueryCachingPolicy(),
            10);
    String line = report("allow-flag revoke 0, UsageTrackingQueryCachingPolicy", r);
    assertEquals(line, r[1], r[2]);
  }
}
