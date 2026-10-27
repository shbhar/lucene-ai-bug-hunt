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
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Probe: a valid update that moves q1 from tenant A to tenant B, followed in the same call by a
 * query that IndexWriter rejects. Until the next write, is q1 reported for a tenant-A document that
 * matches only its new query? Neither version should report it (old: "a" in A; new: "c" in B).
 */
public class TestFilterChangeBeforeRejected extends LuceneTestCase {

  /** "xx" is a term query; "xx+y" is the conjunction +f:xx +f:y. */
  private static Query parse(String t) {
    String[] parts = t.split("\\+");
    if (parts.length == 1) return new TermQuery(new Term("f", t));
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (String x : parts) b.add(new TermQuery(new Term("f", x)), Occur.MUST);
    return b.build();
  }

  private static MonitorQuery mq(String id, String t, String tenant) {
    return new MonitorQuery(id, parse(t), t, Map.of("tenant", tenant));
  }

  private static boolean m(Monitor mon, String id, String text, String tenant) throws IOException {
    Document d = new Document();
    d.add(new TextField("f", text, Field.Store.NO));
    d.add(new TextField("tenant", tenant, Field.Store.NO));
    return mon.match(d, QueryMatch.SIMPLE_MATCHER).matches(id) != null;
  }

  /** Old q1 = +xx +y in tenant A; new q1 = xx in tenant B; the document "xx" in tenant A matches neither. */
  public void testNarrowingTenantBroadeningQueryBeforeRejectedQuery() throws IOException {
    Presearcher p =
        new TermFilteredPresearcher(
            TermFilteredPresearcher.DEFAULT_WEIGHTOR, List.of(), Set.of("tenant"));
    try (Monitor mon = new Monitor(new WhitespaceAnalyzer(), p)) {
      mon.register(mq("q1", "xx+y", "A"));
      String before =
          "xx-y/A=" + m(mon, "q1", "xx y", "A") + " xx/A=" + m(mon, "q1", "xx", "A");
      expectThrows(
          IllegalArgumentException.class,
          () -> mon.register(mq("q1", "xx", "B"), mq("q2", "z", "x".repeat(40_000))));
      String live =
          "xx/A=" + m(mon, "q1", "xx", "A") + " xx/B=" + m(mon, "q1", "xx", "B");
      mon.register(mq("q3", "z", "A"));
      String afterNext =
          "xx/A=" + m(mon, "q1", "xx", "A") + " xx/B=" + m(mon, "q1", "xx", "B");
      System.out.println("PROBE2 before: " + before);
      System.out.println("PROBE2 live after the failed call: " + live);
      System.out.println("PROBE2 after the next register: " + afterNext);
      assertFalse(
          "live: q1 reported for the tenant-A document xx, which neither version matches",
          live.contains("xx/A=true"));
    }
  }

  public void testTenantChangeBeforeRejectedQuery() throws IOException {
    Presearcher p =
        new TermFilteredPresearcher(
            TermFilteredPresearcher.DEFAULT_WEIGHTOR, List.of(), Set.of("tenant"));
    try (Monitor mon = new Monitor(new WhitespaceAnalyzer(), p)) {
      mon.register(mq("q1", "a", "A"));
      String before = "a/A=" + m(mon, "q1", "a", "A") + " c/A=" + m(mon, "q1", "c", "A");
      expectThrows(
          IllegalArgumentException.class,
          () -> mon.register(mq("q1", "c", "B"), mq("q2", "z", "x".repeat(40_000))));
      String live =
          "a/A="
              + m(mon, "q1", "a", "A")
              + " c/A="
              + m(mon, "q1", "c", "A")
              + " c/B="
              + m(mon, "q1", "c", "B");
      mon.register(mq("q3", "z", "A"));
      String afterNext =
          "a/A="
              + m(mon, "q1", "a", "A")
              + " c/A="
              + m(mon, "q1", "c", "A")
              + " c/B="
              + m(mon, "q1", "c", "B");
      System.out.println("PROBE before: " + before);
      System.out.println("PROBE live after the failed call: " + live);
      System.out.println("PROBE after the next register: " + afterNext);
      assertFalse(
          "live: q1 reported for c in tenant A, which neither version allows", live.contains("c/A=true"));
    }
  }
}
