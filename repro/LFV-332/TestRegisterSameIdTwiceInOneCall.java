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
import java.nio.file.Path;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Registering a query id that is already registered replaces the old query, as
 * TestMonitor#testUpdatesOverwriteOldQueries shows for two separate register calls. The same must
 * hold when one register call contains both versions of the id.
 */
public class TestRegisterSameIdTwiceInOneCall extends LuceneTestCase {

  private static final String FIELD = "text";
  private static final Analyzer ANALYZER = new WhitespaceAnalyzer();
  private static final MonitorQuerySerializer SERIALIZER =
      MonitorQuerySerializer.fromParser(TestRegisterSameIdTwiceInOneCall::parse);

  /** "x" is a term query, "x OR y" a disjunction of two term queries. */
  private static Query parse(String query) {
    String[] terms = query.split(" OR ");
    if (terms.length == 1) {
      return new TermQuery(new Term(FIELD, terms[0]));
    }
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (String term : terms) {
      builder.add(new TermQuery(new Term(FIELD, term)), Occur.SHOULD);
    }
    return builder.build();
  }

  private static MonitorQuery mq(String id, String query) {
    return new MonitorQuery(id, parse(query), query, Map.of());
  }

  /** Which of the one-word documents apple, banana and cherry query "q" is reported for. */
  private static String matches(Monitor monitor) throws IOException {
    StringBuilder sb = new StringBuilder();
    for (String word : new String[] {"apple", "banana", "cherry"}) {
      Document doc = new Document();
      doc.add(new TextField(FIELD, word, Field.Store.NO));
      if (monitor.match(doc, QueryMatch.SIMPLE_MATCHER).matches("q") != null) {
        sb.append(sb.length() == 0 ? "" : ",").append(word);
      }
    }
    return "{" + sb + "}";
  }

  /** Whichever entry wins, q must match like one of its versions. */
  public void testOneCallBehavesLikeOneOfTheVersions() throws IOException {
    try (Monitor monitor = new Monitor(ANALYZER)) {
      monitor.register(mq("q", "apple OR banana"), mq("q", "cherry"));
      String got = matches(monitor);
      assertTrue(
          "q matches " + got + ", but the versions match {apple,banana} and {cherry}",
          got.equals("{apple,banana}") || got.equals("{cherry}"));
    }
  }

  /** The later entry replaces the earlier one, as it does across two calls. */
  public void testLaterEntryReplacesEarlierOne() throws IOException {
    try (Monitor monitor = new Monitor(ANALYZER)) {
      monitor.register(mq("q", "apple OR banana"));
      monitor.register(mq("q", "cherry"));
      assertEquals("two calls", "{cherry} disjuncts=1", state(monitor));
    }
    try (Monitor monitor = new Monitor(ANALYZER)) {
      monitor.register(mq("q", "apple OR banana"), mq("q", "cherry"));
      String got = state(monitor);
      assertEquals("one call gave " + got, "{cherry} disjuncts=1", got);
    }
  }

  /** A persistent index gives one answer: live, after a reopen, read-only, and in getQuery. */
  public void testPersistentViewsAgree() throws IOException {
    String expected = "getQuery=cherry live={cherry} reopened={cherry} readOnly={cherry}";
    assertEquals("two calls", expected, views(false, "apple OR banana", "cherry"));
    String got = views(true, "apple OR banana", "cherry");
    assertEquals("one call gave " + got, expected, got);
  }

  /** The same with two single-term versions, where the live Monitor happens to be right. */
  public void testPersistentViewsAgreeForTermQueries() throws IOException {
    String expected = "getQuery=cherry live={cherry} reopened={cherry} readOnly={cherry}";
    assertEquals("two calls", expected, views(false, "apple", "cherry"));
    String got = views(true, "apple", "cherry");
    assertEquals("one call gave " + got, expected, got);
  }

  private static String state(Monitor monitor) throws IOException {
    return matches(monitor) + " disjuncts=" + monitor.getDisjunctCount();
  }

  private String views(boolean oneCall, String first, String second) throws IOException {
    Path path = createTempDir();
    MonitorConfiguration writable = new MonitorConfiguration().setIndexPath(path, SERIALIZER);
    String live;
    try (Monitor monitor = new Monitor(ANALYZER, writable)) {
      if (oneCall) {
        monitor.register(mq("q", first), mq("q", second));
      } else {
        monitor.register(mq("q", first));
        monitor.register(mq("q", second));
      }
      live = matches(monitor);
    }
    String stored;
    String reopened;
    try (Monitor monitor = new Monitor(ANALYZER, writable)) {
      stored = monitor.getQuery("q").getQueryString();
      reopened = matches(monitor);
    }
    MonitorConfiguration readOnly =
        new MonitorConfiguration()
            .setDirectoryProvider(() -> FSDirectory.open(path), SERIALIZER, true);
    String readOnlyMatches;
    try (Monitor monitor = new Monitor(ANALYZER, readOnly)) {
      readOnlyMatches = matches(monitor);
    }
    return "getQuery="
        + stored
        + " live="
        + live
        + " reopened="
        + reopened
        + " readOnly="
        + readOnlyMatches;
  }
}
