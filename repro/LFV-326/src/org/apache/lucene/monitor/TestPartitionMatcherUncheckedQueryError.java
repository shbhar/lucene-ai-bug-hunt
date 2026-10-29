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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestPartitionMatcherUncheckedQueryError extends LuceneTestCase {

  public void testUncheckedQueryErrorIsReportedPerQuery() throws Exception {
    Document doc = new Document();
    doc.add(new LongPoint("num", 5));
    try (ExecutorService executor = Executors.newFixedThreadPool(2);
        Monitor monitor = new Monitor(new StandardAnalyzer())) {
      monitor.register(
          new MonitorQuery("long", LongPoint.newExactQuery("num", 5)),
          new MonitorQuery("int", IntPoint.newExactQuery("num", 5)));
      MatchingQueries<QueryMatch> matches;
      try {
        matches =
            monitor.match(doc, PartitionMatcher.factory(executor, QueryMatch.SIMPLE_MATCHER, 2));
      } catch (RuntimeException e) {
        // the default serial matcher returns the match of "long" and reports "int" in getErrors()
        throw new AssertionError(
            "expected 1 match and an error for query \"int\", but Monitor.match threw " + e, e);
      }
      assertEquals(1, matches.getMatchCount());
      assertTrue(matches.getErrors().containsKey("int"));
    }
  }
}
