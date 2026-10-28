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
package monitorverify;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.monitor.MatchingQueries;
import org.apache.lucene.monitor.Monitor;
import org.apache.lucene.monitor.MonitorQuery;
import org.apache.lucene.monitor.QueryDecomposer;
import org.apache.lucene.monitor.QueryMatch;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A BooleanQuery with minimumNumberShouldMatch=1 but no SHOULD clause that can match matches no
 * document (TestBooleanMinShouldMatch#testNoOptionalButMin2 in core asserts 0 hits for one MUST
 * clause with min=1). The Monitor must therefore not report it as a match. Uses only public API
 * (this class is outside the org.apache.lucene.monitor package). Ground truth is a regular index
 * searched with IndexSearcher.
 */
public class TestMonitorMsmOneRequiredClause extends LuceneTestCase {

  private static final String F = "text";
  private static final String DOC_TEXT = "alpha beta";

  private static Query t(String v) {
    return new TermQuery(new Term(F, v));
  }

  /** (one required clause, plus the given extra clauses) with minimumNumberShouldMatch = msm */
  private static Query bq(int msm, Object... occurAndQuery) {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    b.setMinimumNumberShouldMatch(msm);
    for (int i = 0; i < occurAndQuery.length; i += 2) {
      b.add((Query) occurAndQuery[i + 1], (Occur) occurAndQuery[i]);
    }
    return b.build();
  }

  /** Queries that the claim says are mis-decomposed: none of them can match any document. */
  private static Map<String, Query> suspectQueries() {
    Map<String, Query> m = new LinkedHashMap<>();
    // one MUST clause, no SHOULD clause, msm=1
    m.put("mustOnly", bq(1, Occur.MUST, t("alpha")));
    // one FILTER clause, no SHOULD clause, msm=1
    m.put("filterOnly", bq(1, Occur.FILTER, t("alpha")));
    // MUST + MUST_NOT (exclusion not present in the doc), msm=1
    m.put("mustAndExclusion", bq(1, Occur.MUST, t("alpha"), Occur.MUST_NOT, t("omega")));
    // MUST + a SHOULD clause that is a pure-negative BooleanQuery (matches nothing), msm=1
    m.put(
        "mustAndPureNegativeShould",
        bq(1, Occur.MUST, t("alpha"), Occur.SHOULD, bq(0, Occur.MUST_NOT, t("omega"))));
    // the same shape nested inside a disjunction whose other branch does not match the doc
    m.put("nestedInDisjunction", bq(0, Occur.SHOULD, t("zzz"), Occur.SHOULD, bq(1, Occur.MUST, t("alpha"))));
    // wrapped in a non-unit boost
    m.put("boosted", new BoostQuery(bq(1, Occur.MUST, t("alpha")), 2f));
    // inside a DisjunctionMaxQuery
    m.put(
        "inDisMax",
        new DisjunctionMaxQuery(List.of(bq(1, Occur.MUST, t("alpha")), t("zzz")), 0f));
    return m;
  }

  /** Controls that must match the doc (non-vacuity: the Monitor does report real matches). */
  private static Map<String, Query> controlQueries() {
    Map<String, Query> m = new LinkedHashMap<>();
    m.put("ctl_mustOnly_msm0", bq(0, Occur.MUST, t("alpha")));
    m.put("ctl_mustAndShould_msm1", bq(1, Occur.MUST, t("alpha"), Occur.SHOULD, t("beta")));
    m.put("ctl_shouldOnly_msm1", bq(1, Occur.SHOULD, t("alpha"), Occur.SHOULD, t("zzz")));
    m.put("ctl_mustAndExclusion_msm0", bq(0, Occur.MUST, t("alpha"), Occur.MUST_NOT, t("omega")));
    // and one control that must NOT match (Monitor correctly rejects it)
    m.put("ctl_nomatch_mustAndShould_msm1", bq(1, Occur.MUST, t("alpha"), Occur.SHOULD, t("zzz")));
    return m;
  }

  private static Document doc() {
    Document d = new Document();
    d.add(new TextField(F, DOC_TEXT, Field.Store.NO));
    return d;
  }

  /** Ground truth: the ids whose query has at least one hit on DOC_TEXT in a regular index. */
  private static Set<String> truth(Analyzer analyzer, Map<String, Query> queries)
      throws IOException {
    Set<String> ids = new TreeSet<>();
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        w.addDocument(doc());
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        for (Map.Entry<String, Query> e : queries.entrySet()) {
          if (s.count(e.getValue()) > 0) ids.add(e.getKey());
        }
      }
    }
    return ids;
  }

  private static Set<String> monitorMatches(Analyzer analyzer, Map<String, Query> queries)
      throws IOException {
    Set<String> ids = new TreeSet<>();
    try (Monitor monitor = new Monitor(analyzer)) { // default configuration and decomposer
      for (Map.Entry<String, Query> e : queries.entrySet()) {
        monitor.register(new MonitorQuery(e.getKey(), e.getValue()));
      }
      MatchingQueries<QueryMatch> res = monitor.match(doc(), QueryMatch.SIMPLE_MATCHER);
      for (QueryMatch qm : res.getMatches()) ids.add(qm.getQueryId());
    }
    return ids;
  }

  /** End-to-end: Monitor.match must report exactly the registered queries that match the doc. */
  public void testMonitorReportsOnlyMatchingQueries() throws IOException {
    Analyzer analyzer = new StandardAnalyzer();
    Map<String, Query> all = new LinkedHashMap<>(controlQueries());
    all.putAll(suspectQueries());

    Set<String> expected = truth(analyzer, all);
    // sanity on the oracle itself: controls behave as designed, suspects match nothing
    assertEquals(
        Set.of("ctl_mustOnly_msm0", "ctl_mustAndShould_msm1", "ctl_shouldOnly_msm1",
            "ctl_mustAndExclusion_msm0"),
        expected);

    Set<String> actual = monitorMatches(analyzer, all);
    Set<String> falsePositives = new TreeSet<>(actual);
    falsePositives.removeAll(expected);
    Set<String> misses = new TreeSet<>(expected);
    misses.removeAll(actual);
    StringBuilder sb = new StringBuilder();
    for (String id : falsePositives) {
      sb.append("\n  ").append(id).append(" = ").append(all.get(id));
      sb.append("  decomposed to ").append(new QueryDecomposer().decompose(all.get(id)));
    }
    assertTrue(
        "Monitor.match reported queries that match nothing (false positives):"
            + sb
            + "\n  misses="
            + misses
            + "\n  expected="
            + expected
            + "\n  actual="
            + actual,
        falsePositives.isEmpty() && misses.isEmpty());
  }

  /**
   * Lower level: the union of the decomposed parts must match the doc iff the original does
   * (Monitor runs the parts instead of the original and reports the parent id on any hit).
   */
  public void testDecompositionPreservesMatching() throws IOException {
    Analyzer analyzer = new StandardAnalyzer();
    QueryDecomposer decomposer = new QueryDecomposer();
    StringBuilder bad = new StringBuilder();
    Map<String, Query> all = new LinkedHashMap<>(controlQueries());
    all.putAll(suspectQueries());
    for (Map.Entry<String, Query> e : all.entrySet()) {
      Map<String, Query> one = new LinkedHashMap<>();
      one.put("orig", e.getValue());
      int i = 0;
      Set<Query> parts = decomposer.decompose(e.getValue());
      for (Query part : parts) one.put("part" + (i++), part);
      Set<String> hits = truth(analyzer, one);
      boolean origMatches = hits.contains("orig");
      boolean anyPartMatches = hits.stream().anyMatch(h -> h.startsWith("part"));
      if (origMatches != anyPartMatches) {
        bad.append("\n  ")
            .append(e.getKey())
            .append(": ")
            .append(e.getValue())
            .append(" matches=")
            .append(origMatches)
            .append(" but decomposed ")
            .append(parts)
            .append(" matches=")
            .append(anyPartMatches);
      }
    }
    assertTrue("QueryDecomposer changed matching semantics:" + bad, bad.length() == 0);
  }
}
