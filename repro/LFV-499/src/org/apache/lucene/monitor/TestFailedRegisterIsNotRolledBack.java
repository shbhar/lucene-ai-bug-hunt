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
import java.util.Map;
import java.util.Set;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A register() call that the query index's IndexWriter rejects part-way (here: a filter-field
 * metadata value that is a single term longer than IndexWriter.MAX_TERM_LENGTH) throws, but it
 * is not rolled back: the deletes of every id in the call and the cache entries written before the
 * failure stay in effect. Right away the old version of an updated query is run with the new
 * version's query; at the next commit (any later register/deleteById, or close()) the previous
 * versions of all queries in the failed call are deleted for good.
 */
public class TestFailedRegisterIsNotRolledBack extends LuceneTestCase {

  private static Query parse(String s) {
    int i = s.indexOf(':');
    return new TermQuery(new Term(s.substring(0, i), s.substring(i + 1)));
  }

  private static final MonitorQuerySerializer SERIALIZER =
      MonitorQuerySerializer.fromParser(TestFailedRegisterIsNotRolledBack::parse);

  private static MonitorQuery mq(String id, String query, Map<String, String> metadata) {
    return new MonitorQuery(id, parse(query), query, metadata);
  }

  private static Document doc(String text) {
    Document d = new Document();
    d.add(new TextField("f", text, Field.Store.NO));
    return d;
  }

  private static Monitor open(Path path) throws IOException {
    Presearcher presearcher =
        new TermFilteredPresearcher(
            TermFilteredPresearcher.DEFAULT_WEIGHTOR, List.of(), Set.of("lang"));
    return new Monitor(
        new WhitespaceAnalyzer(),
        presearcher,
        new MonitorConfiguration().setIndexPath(path, SERIALIZER));
  }

  /** 40,000 bytes: indexed as one term by the query index's KeywordAnalyzer, which IndexWriter rejects */
  private static final Map<String, String> REJECTED_METADATA = Map.of("lang", "x".repeat(40_000));

  private static boolean matches(Monitor monitor, String id, String text) throws IOException {
    return monitor.match(doc(text), QueryMatch.SIMPLE_MATCHER).matches(id) != null;
  }

  /** The failed update of q2 must leave q2 (and q1, in the same call) as they were. */
  public void testFailedUpdateDeletesPreviousVersionsAfterReopen() throws IOException {
    Path path = createTempDir();
    try (Monitor monitor = open(path)) {
      monitor.register(mq("q1", "f:a", Map.of()), mq("q2", "f:b", Map.of()));
      expectThrows(
          IllegalArgumentException.class,
          () -> monitor.register(mq("q2", "f:y", REJECTED_METADATA), mq("q1", "f:c", Map.of())));
    }
    try (Monitor monitor = open(path)) {
      assertNotNull("q2 was deleted by a register() call that threw", monitor.getQuery("q2"));
      assertNotNull("q1 was deleted by a register() call that threw", monitor.getQuery("q1"));
    }
  }

  /** Right after the failure, q2 must still match what its registered version matches. */
  public void testFailedUpdateRunsNewQueryOnOldIndexEntry() throws IOException {
    try (Monitor monitor = open(createTempDir())) {
      monitor.register(mq("q2", "f:b", Map.of()));
      expectThrows(
          IllegalArgumentException.class, () -> monitor.register(mq("q2", "f:y", REJECTED_METADATA)));
      assertEquals("f:b", monitor.getQuery("q2").getQueryString());
      assertTrue("getQuery says q2 is f:b, but q2 does not match 'b'", matches(monitor, "q2", "b"));
    }
  }

  /** A later, unrelated register() makes the failed call's deletes permanent. */
  public void testNextRegisterCommitsTheFailedCallsDeletes() throws IOException {
    try (Monitor monitor = open(createTempDir())) {
      monitor.register(mq("q2", "f:b", Map.of()));
      expectThrows(
          IllegalArgumentException.class, () -> monitor.register(mq("q2", "f:y", REJECTED_METADATA)));
      monitor.register(mq("q3", "f:z", Map.of()));
      assertEquals(Set.of("q2", "q3"), monitor.getQueryIds());
    }
  }
}
