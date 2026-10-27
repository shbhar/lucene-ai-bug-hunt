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
import java.util.List;
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
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Registering a MonitorQuery whose id is already registered replaces the old query
 * (TestMonitor#testUpdatesOverwriteOldQueries). WritableQueryIndex.commitWithoutNotify first deletes
 * every id of the batch and then adds every update, so when one register() call contains the same id
 * twice, both versions stay in the query index. Decomposed branches of the older version keep
 * matching, and after reopening a persistent Monitor the older version replaces the newer one.
 *
 * <p>Uses only public API.
 */
public class TestRegisterDuplicateIdInBatch extends LuceneTestCase {

  private static final String FIELD = "text";
  private static final Analyzer ANALYZER = new WhitespaceAnalyzer();

  /** Parses "x" as a term query and "x OR y" as a two-clause disjunction (for the serializer). */
  private static Query parse(String s) {
    String[] terms = s.split(" OR ");
    if (terms.length == 1) {
      return new TermQuery(new Term(FIELD, terms[0]));
    }
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (String t : terms) {
      b.add(new TermQuery(new Term(FIELD, t)), Occur.SHOULD);
    }
    return b.build();
  }

  private static MonitorQuery mq(String id, String query) {
    return new MonitorQuery(id, parse(query), query, java.util.Map.of());
  }

  private static Document doc(String text) {
    Document doc = new Document();
    doc.add(new TextField(FIELD, text, Field.Store.NO));
    return doc;
  }

  /** The documents (apple, banana, cherry) that q is reported for. */
  private static String matched(Monitor monitor) throws IOException {
    StringBuilder sb = new StringBuilder();
    for (String text : new String[] {"apple", "banana", "cherry"}) {
      if (monitor.match(doc(text), QueryMatch.SIMPLE_MATCHER).matches("q") != null) {
        sb.append(sb.length() == 0 ? "" : ",").append(text);
      }
    }
    return "{" + sb + "}";
  }

  public void testOneBatchBehavesLikeOneOfTheVersions() throws IOException {
    try (Monitor monitor = new Monitor(ANALYZER)) {
      // one register() call: "q" is created and then edited
      monitor.register(List.of(mq("q", "apple OR banana"), mq("q", "cherry")));
      // whichever entry wins, q must behave like one version: {apple,banana} (first) or {cherry} (last)
      String got = matched(monitor);
      assertTrue(
          "q matches " + got + ", which is neither version ({apple,banana} or {cherry})",
          got.equals("{apple,banana}") || got.equals("{cherry}"));
    }
  }

  public void testLastVersionInOneBatchWins() throws IOException {
    try (Monitor monitor = new Monitor(ANALYZER)) {
      monitor.register(List.of(mq("q", "apple OR banana"), mq("q", "cherry")));
      assertEquals("{cherry}", matched(monitor));
      assertEquals(1, monitor.getDisjunctCount());
    }
  }

  public void testLastVersionInOneBatchWinsAfterReopen() throws IOException {
    Path path = createTempDir();
    MonitorConfiguration config =
        new MonitorConfiguration()
            .setIndexPath(path, MonitorQuerySerializer.fromParser(TestRegisterDuplicateIdInBatch::parse));
    try (Monitor monitor = new Monitor(ANALYZER, config)) {
      monitor.register(List.of(mq("q", "apple"), mq("q", "cherry")));
      // before the reopen the newest version is the one that runs
      assertNull(monitor.match(doc("apple"), QueryMatch.SIMPLE_MATCHER).matches("q"));
    }
    try (Monitor monitor = new Monitor(ANALYZER, config)) {
      // getQuery and matching must agree on which version is registered
      assertEquals("cherry", monitor.getQuery("q").getQueryString());
      assertNull(
          "after reopening, q is text:cherry, but the first version text:apple matches",
          monitor.match(doc("apple"), QueryMatch.SIMPLE_MATCHER).matches("q"));
      assertNotNull(monitor.match(doc("cherry"), QueryMatch.SIMPLE_MATCHER).matches("q"));
    }
  }
}
