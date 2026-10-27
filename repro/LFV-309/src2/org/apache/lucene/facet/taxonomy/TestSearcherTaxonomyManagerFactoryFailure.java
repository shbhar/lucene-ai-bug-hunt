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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.taxonomy.SearcherTaxonomyManager.SearcherAndTaxonomy;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SearcherTaxonomyManager.refreshIfNeeded first reopens the index reader, then reopens (or
 * incRefs) the taxonomy reader, and only then calls SearcherManager.getSearcher. getSearcher
 * releases the new index reader when the SearcherFactory throws, but nothing releases the taxonomy
 * reader reference taken just before, so every failed refresh leaks one taxonomy reader
 * reference (or a whole newly opened taxonomy reader). Only public API is used.
 */
public class TestSearcherTaxonomyManagerFactoryFailure extends LuceneTestCase {

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

  /** Only the index changed: the current taxonomy reader is incRef'd and never decRef'd. */
  public void testUnchangedTaxonomyReaderNotLeaked() throws Exception {
    Directory indexDir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter w = new IndexWriter(indexDir, newIndexWriterConfig());
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir);
    FailingFactory factory = new FailingFactory();
    SearcherTaxonomyManager mgr = new SearcherTaxonomyManager(w, factory, tw);

    SearcherAndTaxonomy pair = mgr.acquire();
    DirectoryTaxonomyReader taxoReader = pair.taxonomyReader;
    mgr.release(pair);
    int refCountBefore = taxoReader.getRefCount();

    w.addDocument(new Document()); // the index changes, the taxonomy does not
    factory.failNext.set(true);
    expectThrows(IOException.class, mgr::maybeRefresh);
    int refCountAfterFailedRefresh = taxoReader.getRefCount();

    // undo the leak (if any) so that everything can be closed cleanly below
    for (int i = refCountAfterFailedRefresh; i > refCountBefore; i--) {
      taxoReader.decRef();
    }
    mgr.close();
    tw.close();
    w.close();
    taxoDir.close();
    indexDir.close();

    assertEquals(
        "a failed refresh left an extra reference on the manager's taxonomy reader",
        refCountBefore,
        refCountAfterFailedRefresh);
  }

  /** Index and taxonomy changed: the newly opened taxonomy reader is never closed. */
  public void testReopenedTaxonomyReaderNotLeaked() throws Exception {
    Directory indexDir = newDirectory();
    MockDirectoryWrapper taxoDir = newMockDirectory(); // tracks files left open at close()
    IndexWriter w = new IndexWriter(indexDir, newIndexWriterConfig());
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir);
    FailingFactory factory = new FailingFactory();
    SearcherTaxonomyManager mgr = new SearcherTaxonomyManager(w, factory, tw);

    tw.addCategory(new FacetLabel("a", "b")); // the taxonomy changes
    w.addDocument(new Document()); // and the index changes
    factory.failNext.set(true);
    expectThrows(IOException.class, mgr::maybeRefresh);

    mgr.close();
    tw.close();
    w.close();
    indexDir.close();
    // MockDirectoryWrapper.close() fails if a reader still holds files of the directory open
    RuntimeException leak = null;
    try {
      taxoDir.close();
    } catch (RuntimeException e) {
      leak = e;
    }
    assertNull(
        "taxonomy files are still open after every manager and writer was closed: " + leak, leak);
  }
}
