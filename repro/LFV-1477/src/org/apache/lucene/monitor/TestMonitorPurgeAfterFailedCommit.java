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
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMonitorPurgeAfterFailedCommit extends LuceneTestCase {

  public void testPurgeAfterFailedCommitKeepsStoredQueryMatchable() throws IOException {
    AtomicBoolean failNextSync = new AtomicBoolean();
    Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public void sync(Collection<String> names) throws IOException {
            if (failNextSync.getAndSet(false)) {
              throw new IOException("simulated sync failure");
            }
            super.sync(names);
          }
        };
    MonitorConfiguration config =
        new MonitorConfiguration()
            .setDirectoryProvider(
                () -> dir, MonitorQuerySerializer.fromParser(s -> new TermQuery(new Term("f", s))));
    try (Monitor monitor = new Monitor(new StandardAnalyzer(), config)) {
      failNextSync.set(true);
      expectThrows(
          IOException.class,
          () -> monitor.register(new MonitorQuery("q1", new TermQuery(new Term("f", "apple")), "apple", Map.of())));
      monitor.purgeCache();
      monitor.register(new MonitorQuery("q2", new TermQuery(new Term("f", "banana")), "banana", Map.of()));

      Document doc = new Document();
      doc.add(new TextField("f", "apple", Field.Store.NO));
      boolean stored = monitor.getQuery("q1") != null;
      boolean matched = monitor.match(doc, QueryMatch.SIMPLE_MATCHER).matches("q1") != null;
      // Expected: a query the Monitor lists as stored also matches; actual: stored=true, matched=false.
      assertEquals("q1 stored=" + stored + " but matched=" + matched, stored, matched);
    }
  }
}
