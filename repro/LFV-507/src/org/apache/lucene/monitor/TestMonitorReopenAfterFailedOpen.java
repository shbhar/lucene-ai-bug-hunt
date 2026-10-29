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
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMonitorReopenAfterFailedOpen extends LuceneTestCase {

  private static Monitor open(Path path, MonitorQuerySerializer serializer) throws IOException {
    return new Monitor(
        new WhitespaceAnalyzer(), new MonitorConfiguration().setIndexPath(path, serializer));
  }

  public void testReopenAfterRejectedStoredQuery() throws IOException {
    Path path = createTempDir();
    MonitorQuerySerializer serializer =
        MonitorQuerySerializer.fromParser(s -> new TermQuery(new Term("f", s)));
    try (Monitor monitor = open(path, serializer)) {
      monitor.register(new MonitorQuery("q", new TermQuery(new Term("f", "a")), "a", Map.of()));
    }
    MonitorQuerySerializer rejecting =
        MonitorQuerySerializer.fromParser(
            s -> {
              throw new IllegalArgumentException("cannot parse " + s);
            });
    expectThrows(IllegalStateException.class, () -> open(path, rejecting));
    try (Monitor monitor = open(path, serializer)) {
      assertEquals(1, monitor.getQueryCount());
    } catch (LockObtainFailedException e) {
      throw new AssertionError(
          "expected the retry with the working serializer to open the index, but got " + e, e);
    }
  }
}
