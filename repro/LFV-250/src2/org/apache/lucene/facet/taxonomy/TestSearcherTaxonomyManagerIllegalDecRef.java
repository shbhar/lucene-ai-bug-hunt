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
package org.apache.lucene.facet.taxonomy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.taxonomy.SearcherTaxonomyManager.SearcherAndTaxonomy;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * ReferenceManager.acquire() throws IllegalStateException when the managed reference was closed
 * behind the manager's back ("this is likely a bug when the reference count is modified outside of
 * the ReferenceManager"); TestSearcherManager.testReferenceDecrementIllegally checks this for
 * SearcherManager. For SearcherTaxonomyManager the check never fires when only the taxonomy reader
 * was closed: tryIncRef fails, but getRefCount reports only the index reader's count, so acquire()
 * retries forever in a busy loop. This test mirrors testReferenceDecrementIllegally with an extra
 * decRef on the taxonomy reader. Only public API is used.
 */
public class TestSearcherTaxonomyManagerIllegalDecRef extends LuceneTestCase {

  public void testTaxonomyReaderDecrementIllegally() throws Exception {
    Directory indexDir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter w = new IndexWriter(indexDir, newIndexWriterConfig());
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir);
    SearcherTaxonomyManager mgr = new SearcherTaxonomyManager(w, null, tw);
    w.addDocument(new Document());
    mgr.maybeRefreshBlocking();

    SearcherAndTaxonomy pair = mgr.acquire();
    pair.taxonomyReader.decRef(); // application bug: an unmatched decRef
    mgr.release(pair); // the manager's taxonomy reader is now closed

    CountDownLatch returned = new CountDownLatch(1);
    AtomicReference<Throwable> thrown = new AtomicReference<>();
    Thread t =
        new Thread(
            () -> {
              try {
                SearcherAndTaxonomy p = mgr.acquire();
                mgr.release(p);
              } catch (Throwable e) {
                thrown.set(e);
              } finally {
                returned.countDown();
              }
            });
    t.setDaemon(true);
    t.start();
    boolean didReturn = returned.await(10, TimeUnit.SECONDS);
    try {
      // closing sets the current reference to null, which ends a spinning acquire() with
      // AlreadyClosedException; closing itself then fails on the already closed taxonomy reader
      mgr.close();
    } catch (AlreadyClosedException expected) {
      // the taxonomy reader was closed by the application bug above
    }
    t.join(10_000);
    tw.close();
    w.close();
    taxoDir.close();
    indexDir.close();
    assertTrue(
        "acquire() did not return within 10 s after the taxonomy reader was closed outside the"
            + " manager (busy retry loop); expected IllegalStateException like SearcherManager",
        didReturn);
    assertTrue(
        "expected IllegalStateException, got " + thrown.get(),
        thrown.get() instanceof IllegalStateException);
  }
}
