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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestConcurrentQueryLoaderSmallQueue extends LuceneTestCase {

  public void testQueueSmallerThanThreadCount() throws Exception {
    try (Monitor monitor = new Monitor(new StandardAnalyzer())) {
      try (ConcurrentQueryLoader loader = new ConcurrentQueryLoader(monitor, 2, 1)) {
        loader.add(new MonitorQuery("1", new TermQuery(new Term("f", "a"))));
      }
      // expected 1; actual 0: each worker's batch size is queueSize / threads = 1 / 2 = 0
      assertEquals(1, monitor.getQueryCount());
    }
  }
}
