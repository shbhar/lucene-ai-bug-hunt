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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFailedRegisterDeletesOtherQuery extends LuceneTestCase {

  public void testRejectedQueryDoesNotDeleteOtherQueryOfTheCall() throws IOException {
    Path path = createTempDir();
    try (Monitor monitor = open(path)) {
      monitor.register(query("q1", "a", Map.of()));
      expectThrows(
          IllegalArgumentException.class,
          () ->
              monitor.register(
                  query("q2", "b", Map.of("lang", "x".repeat(40_000))),
                  query("q1", "c", Map.of())));
    }
    try (Monitor monitor = open(path)) {
      // expected: q1 survives (old or new version); actual: [], close() committed its delete
      assertEquals(Set.of("q1"), monitor.getQueryIds());
    }
  }

  private static MonitorQuery query(String id, String term, Map<String, String> metadata) {
    return new MonitorQuery(id, new TermQuery(new Term("f", term)), term, metadata);
  }

  private static Monitor open(Path path) throws IOException {
    return new Monitor(
        new StandardAnalyzer(),
        new TermFilteredPresearcher(
            TermFilteredPresearcher.DEFAULT_WEIGHTOR, List.of(), Set.of("lang")),
        new MonitorConfiguration()
            .setIndexPath(
                path, MonitorQuerySerializer.fromParser(s -> new TermQuery(new Term("f", s)))));
  }
}
