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
package monitorrejectprobe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.monitor.ConcurrentQueryLoader;
import org.apache.lucene.monitor.Monitor;
import org.apache.lucene.monitor.MonitorConfiguration;
import org.apache.lucene.monitor.MonitorQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Probe for the "reject repeated ids" fix option (public API only). It prints what each path does
 * and asserts the behaviour that option gives, so it passes with that option and fails on stock.
 */
public class TestRejectOptionProbe extends LuceneTestCase {

  private static MonitorQuery mq(String id, String term) {
    return new MonitorQuery(id, new TermQuery(new Term("text", term)));
  }

  /** One register call with a repeated id is rejected, and nothing of it is registered. */
  public void testOneCallIsRejected() throws IOException {
    try (Monitor monitor = new Monitor(new WhitespaceAnalyzer())) {
      String outcome;
      try {
        monitor.register(mq("q", "apple"), mq("q", "cherry"));
        outcome = "accepted";
      } catch (IllegalArgumentException e) {
        outcome = "IllegalArgumentException: " + e.getMessage();
      }
      System.out.println(
          "PROBE one call: " + outcome + "; disjuncts=" + monitor.getDisjunctCount());
      assertTrue(outcome, outcome.startsWith("IllegalArgumentException"));
      assertEquals(0, monitor.getDisjunctCount());
    }
  }

  /** The same two entries 5001 entries apart fall into different internal chunks. */
  public void testRepeatedIdInDifferentChunksIsAccepted() throws IOException {
    try (Monitor monitor =
        new Monitor(new WhitespaceAnalyzer(), new MonitorConfiguration())) {
      List<MonitorQuery> queries = new ArrayList<>();
      queries.add(mq("q", "apple"));
      for (int i = 0; i < 5000; i++) {
        queries.add(mq("filler" + i, "filler"));
      }
      queries.add(mq("q", "cherry"));
      String outcome;
      try {
        monitor.register(queries);
        outcome = "accepted";
      } catch (IllegalArgumentException e) {
        outcome = "IllegalArgumentException: " + e.getMessage();
      }
      System.out.println(
          "PROBE same id 5001 entries apart: "
              + outcome
              + "; queries="
              + monitor.getQueryCount()
              + " disjuncts="
              + monitor.getDisjunctCount());
      assertEquals("accepted", outcome);
    }
  }

  /** What ConcurrentQueryLoader reports when its worker's register call is rejected. */
  public void testConcurrentQueryLoaderWithRepeatedId() throws Exception {
    try (Monitor monitor = new Monitor(new WhitespaceAnalyzer())) {
      String outcome = "close() returned normally";
      try (ConcurrentQueryLoader loader = new ConcurrentQueryLoader(monitor, 1, 10)) {
        loader.add(mq("q", "apple"));
        loader.add(mq("q", "cherry"));
        loader.add(mq("r", "banana"));
      } catch (IOException | RuntimeException e) {
        outcome = "close() threw " + e;
      }
      System.out.println(
          "PROBE ConcurrentQueryLoader: "
              + outcome
              + "; registered ids="
              + monitor.getQueryIds());
    }
  }
}
