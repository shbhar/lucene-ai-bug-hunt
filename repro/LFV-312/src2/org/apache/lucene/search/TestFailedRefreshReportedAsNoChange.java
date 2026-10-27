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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * When a refresh of a {@link ReferenceManager} fails (here: the {@link SearcherFactory} throws while
 * warming the new searcher), {@code ReferenceManager.doMaybeRefresh} still calls every {@link
 * ReferenceManager.RefreshListener#afterRefresh(boolean)} with {@code didRefresh == false}, which is
 * the same signal as "nothing changed". The two refresh listeners in Lucene core treat that signal
 * as "the current searcher already contains everything indexed before the refresh started":
 *
 * <ul>
 *   <li>{@link ControlledRealTimeReopenThread} advances its searching generation, so {@code
 *       waitForGeneration} returns {@code true} and {@code getSearchingGen()} claims a generation
 *       the current searcher does not contain;
 *   <li>{@link LiveFieldValues} drops the values it buffered for the not-yet-visible documents, so
 *       {@code get(id)} answers from the stale searcher.
 * </ul>
 *
 * Only public API is used.
 */
public class TestFailedRefreshReportedAsNoChange extends LuceneTestCase {

  /** A SearcherFactory whose next newSearcher call fails, e.g. because a warm-up query failed. */
  private static final class FailingFactory extends SearcherFactory {
    final AtomicBoolean failNext = new AtomicBoolean();

    @Override
    public IndexSearcher newSearcher(IndexReader reader, IndexReader previousReader)
        throws IOException {
      if (failNext.getAndSet(false)) {
        throw new IOException("simulated warm-up failure");
      }
      return new IndexSearcher(reader);
    }
  }

  private static Document doc(String id, long value) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.NO));
    doc.add(new StoredField("value", value));
    return doc;
  }

  private static int count(SearcherManager mgr, String id) throws IOException {
    IndexSearcher s = mgr.acquire();
    try {
      return s.count(new TermQuery(new Term("id", id)));
    } finally {
      mgr.release(s);
    }
  }

  /** The application refreshes the manager itself (waitForGeneration javadoc allows this). */
  public void testWaitForGenerationAfterFailedRefreshByCaller() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    FailingFactory factory = new FailingFactory();
    SearcherManager mgr = new SearcherManager(w, factory);
    ControlledRealTimeReopenThread<IndexSearcher> crt =
        new ControlledRealTimeReopenThread<>(w, mgr, 60.0, 0.0);
    try {
      long gen = w.addDocument(doc("1", 1));
      factory.failNext.set(true);
      expectThrows(IOException.class, mgr::maybeRefreshBlocking);

      assertEquals("the failed refresh kept the old searcher", 0, count(mgr, "1"));
      // getSearchingGen(): "Returns which generation the current searcher is guaranteed to include"
      assertTrue(
          "getSearchingGen()="
              + crt.getSearchingGen()
              + " >= gen "
              + gen
              + " of a document that the current searcher does not contain",
          crt.getSearchingGen() < gen);
      // waitForGeneration(gen, maxMS): "true if the targetGen is now available"
      assertFalse(
          "waitForGeneration returned true for a generation the current searcher does not contain",
          crt.waitForGeneration(gen, 50));
    } finally {
      crt.close();
      mgr.close();
      w.close();
      dir.close();
    }
  }

  /** The reopen thread itself hits the failure while a searcher thread waits for its document. */
  public void testWaitForGenerationAfterFailedRefreshByReopenThread() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    FailingFactory factory = new FailingFactory();
    SearcherManager mgr = new SearcherManager(w, factory);
    ControlledRealTimeReopenThread<IndexSearcher> crt =
        new ControlledRealTimeReopenThread<>(w, mgr, 60.0, 0.0);
    AtomicReference<Throwable> reopenThreadFailure = new AtomicReference<>();
    // the reopen thread rethrows the refresh failure and ends; keep that failure out of the
    // test framework's uncaught-exception check and record it instead
    crt.setUncaughtExceptionHandler((t, e) -> reopenThreadFailure.set(e));
    crt.setDaemon(true);
    crt.start();
    try {
      long gen = w.addDocument(doc("1", 1));
      factory.failNext.set(true);
      // signals the reopen thread, whose refresh then fails
      boolean visible = crt.waitForGeneration(gen, 5_000);
      crt.join(30_000);
      assertNotNull("the reopen thread's refresh failed", reopenThreadFailure.get());
      int hits = count(mgr, "1");
      assertEquals("the failed refresh kept the old searcher", 0, hits);
      assertFalse(
          "waitForGeneration("
              + gen
              + ") returned true although the current searcher does not contain the document",
          visible);
    } finally {
      crt.close();
      mgr.close();
      w.close();
      dir.close();
    }
  }

  public void testLiveFieldValuesAfterFailedRefresh() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    FailingFactory factory = new FailingFactory();
    SearcherManager mgr = new SearcherManager(w, factory);
    LiveFieldValues<IndexSearcher, Long> live =
        new LiveFieldValues<IndexSearcher, Long>(mgr, -1L) {
          @Override
          protected Long lookupFromSearcher(IndexSearcher s, String id) throws IOException {
            TopDocs hits = s.search(new TermQuery(new Term("id", id)), 1);
            if (hits.scoreDocs.length == 0) {
              return null;
            }
            return s.getIndexReader()
                .storedFields()
                .document(hits.scoreDocs[0].doc)
                .getField("value")
                .numericValue()
                .longValue();
          }
        };
    try {
      w.addDocument(doc("a", 42));
      live.add("a", 42L);
      assertEquals(Long.valueOf(42), live.get("a"));

      factory.failNext.set(true);
      expectThrows(IOException.class, mgr::maybeRefresh);

      assertEquals("the failed refresh kept the old searcher", 0, count(mgr, "a"));
      // get(id): "Returns the current value for this id, or null if the id isn't in the index or
      // was deleted"
      assertEquals(
          "LiveFieldValues forgot the value of a document the searcher cannot see yet",
          Long.valueOf(42),
          live.get("a"));
    } finally {
      live.close();
      mgr.close();
      w.close();
      dir.close();
    }
  }
}
