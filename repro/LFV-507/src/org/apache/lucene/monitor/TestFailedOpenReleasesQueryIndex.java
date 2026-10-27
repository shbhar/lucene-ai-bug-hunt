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
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * When opening a persistent Monitor fails with one of its documented errors (a non-empty query
 * index opened without a MonitorQuerySerializer, or stored queries the serializer cannot parse),
 * the IndexWriter created by the constructor is never closed. It keeps the index's write lock, so
 * the index cannot be opened again by a writable Monitor in the same JVM, even with a correct
 * configuration.
 */
public class TestFailedOpenReleasesQueryIndex extends LuceneTestCase {

  private static Query parse(String s) {
    int i = s.indexOf(':');
    return new TermQuery(new Term(s.substring(0, i), s.substring(i + 1)));
  }

  private static final MonitorQuerySerializer SERIALIZER =
      MonitorQuerySerializer.fromParser(TestFailedOpenReleasesQueryIndex::parse);

  private static Monitor open(Path path, MonitorQuerySerializer serializer) throws IOException {
    return new Monitor(
        new WhitespaceAnalyzer(), new MonitorConfiguration().setIndexPath(path, serializer));
  }

  private static Path indexWithOneQuery() throws IOException {
    Path path = createTempDir();
    try (Monitor monitor = open(path, SERIALIZER)) {
      monitor.register(new MonitorQuery("q", parse("f:a"), "f:a", Map.of()));
    }
    return path;
  }

  public void testReopenAfterMissingSerializer() throws IOException {
    Path path = indexWithOneQuery();
    IllegalStateException e = expectThrows(IllegalStateException.class, () -> open(path, null));
    assertEquals(
        "Attempting to open a non-empty monitor query index with no MonitorQuerySerializer",
        e.getMessage());
    // the caller fixes its configuration and retries
    try (Monitor monitor = open(path, SERIALIZER)) {
      assertEquals(1, monitor.getQueryCount());
    }
  }

  public void testReopenAfterUnparseableQueries() throws IOException {
    Path path = indexWithOneQuery();
    MonitorQuerySerializer failing =
        MonitorQuerySerializer.fromParser(
            s -> {
              throw new IllegalArgumentException("cannot parse " + s);
            });
    IllegalStateException e = expectThrows(IllegalStateException.class, () -> open(path, failing));
    assertEquals("Couldn't parse some queries from the index", e.getMessage());
    // the caller fixes its parser and retries
    try (Monitor monitor = open(path, SERIALIZER)) {
      assertEquals(1, monitor.getQueryCount());
    }
  }
}
