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
package org.apache.lucene.facet.taxonomy.directory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.writercache.LruTaxonomyWriterCache;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * After an addCategory call fails with an IOException that leaves the writer open, a later
 * addCategory of an existing category must still return its existing ordinal (TaxonomyWriter: "If
 * the category was already present in the taxonomy, its existing ordinal is returned.").
 *
 * <p>Each test throws one IOException from openInput while the taxonomy writer opens its
 * near-real-time reader inside the named writer method. The flush before that open succeeds, so
 * IndexWriter treats the exception as non-tragic and stays open, as after a failed memory map or
 * running out of file handles.
 */
public class TestTaxonomyWriterAddCategoryAfterIOException extends LuceneTestCase {

  private static boolean onStack(String classSimpleName, String method) {
    return StackWalker.getInstance()
        .walk(
            s ->
                s.anyMatch(
                    f ->
                        f.getClassName().endsWith("." + classSimpleName)
                            && f.getMethodName().equals(method)));
  }

  private static MockDirectoryWrapper failOnceOpeningReaderIn(
      String writerMethod, AtomicBoolean armed) {
    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    dir.failOn(
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper d) throws IOException {
            if (armed.get()
                && onStack("DirectoryTaxonomyWriter", writerMethod)
                && onStack("StandardDirectoryReader", "open")
                && onStack("MockDirectoryWrapper", "openInput")) {
              armed.set(false);
              throw new IOException("simulated failure while opening a segment file");
            }
          }
        });
    return dir;
  }

  private static Document doc(String value) {
    Document d = new Document();
    d.add(new FacetField("dim", value));
    return d;
  }

  /**
   * Default writer and cache (4000 entries). The document whose new value makes the cache evict
   * fails and is skipped; a later document with the existing value dim/v0 must be counted with the
   * first one.
   */
  private int countOfV0AfterSkippedDocument(boolean inject) throws Exception {
    AtomicBoolean armed = new AtomicBoolean(false);
    MockDirectoryWrapper taxoDir = failOnceOpeningReaderIn("refreshReaderManager", armed);
    Directory indexDir = newDirectory();
    IndexWriter iw = new IndexWriter(indexDir, newIndexWriterConfig());
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir);
    FacetsConfig config = new FacetsConfig();

    // root + "dim" + v0..v3997 = 4000 cache entries
    for (int i = 0; i < 3998; i++) {
      iw.addDocument(config.build(tw, doc("v" + i)));
    }
    if (inject) {
      armed.set(true);
      // the 4001st entry evicts the eldest third of the cache, then the reader refresh fails
      expectThrows(IOException.class, () -> config.build(tw, doc("v3998")));
      assertFalse("the injected failure must have fired", armed.get());
    } else {
      iw.addDocument(config.build(tw, doc("v3998")));
    }
    iw.addDocument(config.build(tw, doc("v0"))); // the application keeps indexing
    tw.commit();
    iw.commit();
    tw.close();
    iw.close();

    try (DirectoryReader r = DirectoryReader.open(indexDir);
        DirectoryTaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
      FacetsCollector fc =
          new IndexSearcher(r).search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
      int count =
          new FastTaxonomyFacetCounts(tr, config, fc).getSpecificValue("dim", "v0").intValue();
      int last = tr.getSize() - 1;
      assertEquals(
          "two documents carry dim/v0; taxonomy size "
              + tr.getSize()
              + ", dim/v0 at ordinal "
              + tr.getOrdinal(new FacetLabel("dim", "v0"))
              + ", ordinal "
              + last
              + " is "
              + tr.getPath(last),
          2,
          count);
      return count;
    } finally {
      indexDir.close();
      taxoDir.close();
    }
  }

  /** Site A, as an application sees it: the facet count of an existing value is split. */
  public void testFacetCountAfterFailedEvictionRefresh() throws Exception {
    countOfV0AfterSkippedDocument(true);
  }

  /** Control: the same documents without the injected failure. */
  public void testControlFacetCountWithoutFailure() throws Exception {
    countOfV0AfterSkippedDocument(false);
  }

  /** Site A: the refresh after a cache eviction fails, so the cache stays marked complete. */
  public void testExistingCategoryAfterFailedEvictionRefresh() throws Exception {
    AtomicBoolean armed = new AtomicBoolean(false);
    MockDirectoryWrapper dir = failOnceOpeningReaderIn("refreshReaderManager", armed);
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir); // default LRU cache of 4000
    for (int i = 0; i < 3999; i++) { // root + l0..l3998 = 4000 cache entries
      assertEquals(i + 1, tw.addCategory(new FacetLabel("l" + i)));
    }
    armed.set(true);
    expectThrows(IOException.class, () -> tw.addCategory(new FacetLabel("l3999")));
    assertFalse("the injected failure must have fired", armed.get());

    int again = tw.addCategory(new FacetLabel("l0")); // l0 has ordinal 1
    tw.commit();
    try (DirectoryTaxonomyReader reader = new DirectoryTaxonomyReader(dir)) {
      assertEquals(
          "taxonomy size "
              + reader.getSize()
              + ", ordinal "
              + again
              + " is "
              + reader.getPath(again),
          1,
          again);
    } finally {
      tw.close();
      dir.close();
    }
  }

  /**
   * Site B: after an existing taxonomy was read into the cache (11 misses), the next new category
   * is indexed, then the reader open for the parent array fails; the retry indexes it again.
   */
  public void testRetryAfterFailedParentArrayInit() throws Exception {
    AtomicBoolean armed = new AtomicBoolean(false);
    MockDirectoryWrapper dir = failOnceOpeningReaderIn("getTaxoArrays", armed);
    try (DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir)) {
      for (int i = 0; i < 12; i++) { // root + l0..l11 = ordinals 0..12
        tw.addCategory(new FacetLabel("l" + i));
      }
    }
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir);
    for (int i = 0; i < 11; i++) {
      assertEquals(i + 1, tw.addCategory(new FacetLabel("l" + i)));
    }
    armed.set(true);
    expectThrows(IOException.class, () -> tw.addCategory(new FacetLabel("new")));
    assertFalse("the injected failure must have fired", armed.get());

    int retry = tw.addCategory(new FacetLabel("new"));
    tw.commit();
    try (DirectoryTaxonomyReader reader = new DirectoryTaxonomyReader(dir)) {
      assertEquals(
          "taxonomy size "
              + reader.getSize()
              + ", ordinal 13 is "
              + reader.getPath(13)
              + ", ordinal "
              + retry
              + " is "
              + reader.getPath(retry),
          reader.getOrdinal(new FacetLabel("new")),
          retry);
    } finally {
      tw.close();
      dir.close();
    }
  }

  /**
   * Site A with a cache fill on the first miss: after the failed refresh the writer's reader is
   * stale, so a fill from it must not mark the cache complete. On stock it fails like the test
   * above; it also catches a fix that clears the flag but still trusts the stale reader.
   */
  public void testFillFromStaleReaderAfterFailedRefresh() throws Exception {
    AtomicBoolean armed = new AtomicBoolean(false);
    MockDirectoryWrapper dir = failOnceOpeningReaderIn("refreshReaderManager", armed);
    DirectoryTaxonomyWriter tw =
        new DirectoryTaxonomyWriter(dir, OpenMode.CREATE, new LruTaxonomyWriterCache(3));
    tw.setCacheMissesUntilFill(0);
    int a = tw.addCategory(new FacetLabel("a"));
    tw.addCategory(new FacetLabel("b"));
    armed.set(true);
    // the 4th cache entry evicts root and "a"; the refresh that follows fails
    expectThrows(IOException.class, () -> tw.addCategory(new FacetLabel("c")));
    assertFalse("the injected failure must have fired", armed.get());
    int again = tw.addCategory(new FacetLabel("a"));
    tw.close();
    dir.close();
    assertEquals(a, again);
  }
}
