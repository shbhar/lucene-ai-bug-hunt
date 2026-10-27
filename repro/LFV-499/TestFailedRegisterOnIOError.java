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
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * An update of a registered query that fails with an IOException (here: the query index cannot
 * create a file while register() flushes, as on a full disk) leaves the query matching nothing:
 * the query cache already holds the new version, while the searcher still holds the old version's
 * presearcher terms. getQuery() reports the old version, which no longer matches its own
 * documents. The IndexWriter is closed by the failure, so this lasts until the Monitor is
 * re-created.
 */
public class TestFailedRegisterOnIOError extends LuceneTestCase {

  private static Document doc(String text) {
    Document d = new Document();
    d.add(new TextField("f", text, Field.Store.NO));
    return d;
  }

  public void testUpdateFailingOnIOErrorMatchesNothing() throws IOException {
    AtomicBoolean diskFull = new AtomicBoolean(false);
    Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexOutput createOutput(String name, IOContext context) throws IOException {
            if (diskFull.get()) {
              throw new IOException("simulated: no space left on device (" + name + ")");
            }
            return super.createOutput(name, context);
          }
        };
    MonitorConfiguration config = new MonitorConfiguration().setDirectoryProvider(() -> dir, null);
    Monitor monitor = new Monitor(new WhitespaceAnalyzer(), config);
    try {
      monitor.register(new MonitorQuery("q", new TermQuery(new Term("f", "a"))));
      assertEquals(1, monitor.match(doc("a"), QueryMatch.SIMPLE_MATCHER).getMatchCount());

      diskFull.set(true);
      expectThrows(
          IOException.class,
          () -> monitor.register(new MonitorQuery("q", new TermQuery(new Term("f", "b")))));
      diskFull.set(false);

      // The failed update is not visible (the old version is still the indexed one), so q must
      // still match what its old version matches - or, if the update had taken effect, 'b'.
      int matchesA = monitor.match(doc("a"), QueryMatch.SIMPLE_MATCHER).getMatchCount();
      int matchesB = monitor.match(doc("b"), QueryMatch.SIMPLE_MATCHER).getMatchCount();
      assertTrue(
          "after the failed update q matches neither its old ('a') nor its new ('b') version",
          matchesA + matchesB > 0);
    } finally {
      try {
        monitor.close();
      } catch (IOException | RuntimeException _) {
        // the IndexWriter was closed by the failure
      }
    }
  }
}
