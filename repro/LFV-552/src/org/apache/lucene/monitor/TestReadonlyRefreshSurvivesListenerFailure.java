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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The periodic refresh of a read-only Monitor catches only IOException. One unchecked exception
 * in a run (here from a MonitorUpdateListener.onPurge; an Error such as OutOfMemoryError during the
 * refresh behaves the same) makes the ScheduledExecutorService suppress every later run, so the
 * read-only Monitor never sees new queries again and no onPurgeError is reported. The writable
 * Monitor's purge task catches Throwable, reports it through onPurgeError and keeps running.
 */
public class TestReadonlyRefreshSurvivesListenerFailure extends LuceneTestCase {

  private static Query parse(String s) {
    int i = s.indexOf(':');
    return new TermQuery(new Term(s.substring(0, i), s.substring(i + 1)));
  }

  private static final MonitorQuerySerializer SERIALIZER =
      MonitorQuerySerializer.fromParser(TestReadonlyRefreshSurvivesListenerFailure::parse);

  public void testRefreshContinuesAfterAFailedRun() throws IOException, InterruptedException {
    Path path = createTempDir();
    try (Monitor writer =
        new Monitor(
            new WhitespaceAnalyzer(), new MonitorConfiguration().setIndexPath(path, SERIALIZER))) {
      writer.register(new MonitorQuery("q1", parse("f:a"), "f:a", Map.of()));

      MonitorConfiguration readConfig =
          new MonitorConfiguration()
              .setPurgeFrequency(50, TimeUnit.MILLISECONDS)
              .setDirectoryProvider(() -> FSDirectory.open(path), SERIALIZER, true);
      try (Monitor reader = new Monitor(new WhitespaceAnalyzer(), readConfig)) {
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch laterRun = new CountDownLatch(1);
        reader.addQueryIndexUpdateListener(
            new MonitorUpdateListener() {
              @Override
              public void onPurge() {
                if (runs.incrementAndGet() == 1) {
                  throw new IllegalStateException("listener fails once");
                }
                laterRun.countDown();
              }
            });
        writer.register(new MonitorQuery("q2", parse("f:a"), "f:a", Map.of()));
        boolean refreshedAgain = laterRun.await(10, TimeUnit.SECONDS);
        assertTrue(
            "the read-only Monitor's periodic refresh stopped after one failed run (runs in 10 s: "
                + runs.get()
                + ")",
            refreshedAgain);
        Document doc = new Document();
        doc.add(new TextField("f", "a", Field.Store.NO));
        assertEquals(2, reader.match(doc, QueryMatch.SIMPLE_MATCHER).getMatchCount());
      }
    }
  }
}
