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
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * After an addCategory call fails with an IOException once the category document has been
 * indexed, DirectoryTaxonomyWriter keeps working but still believes its cache is complete (or that
 * its reader is current), so a later addCategory of an existing category assigns it a second
 * ordinal. TaxonomyWriter.addCategory: "If the category was already present in the taxonomy, its
 * existing ordinal is returned."
 *
 * <p>The failure is a one-shot IOException while the writer opens a near-real-time reader (for
 * example "too many open files" when opening the new segment's files). IndexWriter is not closed by
 * such an exception, and neither is the taxonomy writer.
 */
public class TestTaxonomyWriterFailedAddDuplicatesOrdinal extends LuceneTestCase {

  private static boolean onStack(String classSimpleName, String method) {
    return StackWalker.getInstance()
        .walk(
            s ->
                s.anyMatch(
                    f ->
                        f.getClassName().endsWith("." + classSimpleName)
                            && f.getMethodName().equals(method)));
  }

  /**
   * A directory that throws one IOException from openInput while the taxonomy writer, inside
   * {@code writerMethod}, opens a near-real-time reader (after the flush, so IndexWriter is not
   * closed).
   */
  private static MockDirectoryWrapper failOnceWhileOpeningNrtReaderIn(
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
              throw new IOException("simulated transient failure while opening a segment file");
            }
          }
        });
    return dir;
  }

  /** Default writer and default cache: the failure hits the refresh that the 4001st entry triggers. */
  public void testEvictionRefreshFailureThenReAddGetsSecondOrdinal() throws Exception {
    AtomicBoolean armed = new AtomicBoolean(false);
    MockDirectoryWrapper dir = failOnceWhileOpeningNrtReaderIn("refreshReaderManager", armed);
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir); // default LRU cache of 4000

    // root (ordinal 0) + 3999 labels fill the cache to exactly 4000 entries
    for (int i = 0; i < 3999; i++) {
      assertEquals(i + 1, tw.addCategory(new FacetLabel("l" + i)));
    }
    // the next label makes the cache evict its eldest third (root, l0 .. l1333) and refresh the
    // writer's reader; that refresh fails once
    armed.set(true);
    expectThrows(IOException.class, () -> tw.addCategory(new FacetLabel("l3999")));
    assertFalse("the injected failure must have fired", armed.get());
    assertEquals("the category document was indexed before the failure", 4001, tw.getSize());

    // l0 is already in the taxonomy with ordinal 1
    int again = tw.addCategory(new FacetLabel("l0"));
    int size = tw.getSize();
    tw.commit();
    try (DirectoryTaxonomyReader reader = new DirectoryTaxonomyReader(dir)) {
      assertEquals(
          "addCategory of an existing category must return its existing ordinal; the taxonomy now has size "
              + size
              + " and ordinal "
              + again
              + " is "
              + (again < reader.getSize() ? reader.getPath(again) : "?"),
          1,
          again);
    } finally {
      tw.close();
      dir.close();
    }
  }

  /**
   * Existing taxonomy, default cache: after 11 cache misses the writer reads the whole taxonomy
   * into its cache and closes its reader; the next new category re-opens a reader to initialise
   * the parent array after its document was indexed, and that open fails once.
   */
  public void testParentArrayInitFailureThenRetryGetsSecondOrdinal() throws Exception {
    AtomicBoolean armed = new AtomicBoolean(false);
    MockDirectoryWrapper dir = failOnceWhileOpeningNrtReaderIn("getTaxoArrays", armed);
    try (DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir)) {
      for (int i = 0; i < 12; i++) {
        tw.addCategory(new FacetLabel("l" + i));
      }
    }

    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir); // reopen, default cache
    for (int i = 0; i < 11; i++) { // 11 misses: the cache is then filled with the whole taxonomy
      assertEquals(i + 1, tw.addCategory(new FacetLabel("l" + i)));
    }
    armed.set(true);
    expectThrows(IOException.class, () -> tw.addCategory(new FacetLabel("new")));
    assertFalse("the injected failure must have fired", armed.get());
    assertEquals("the category document was indexed before the failure", 14, tw.getSize());

    int first = tw.addCategory(new FacetLabel("new")); // retry after the transient failure
    int second = tw.addCategory(new FacetLabel("new"));
    tw.commit();
    try (DirectoryTaxonomyReader reader = new DirectoryTaxonomyReader(dir)) {
      int onDisk = reader.getOrdinal(new FacetLabel("new"));
      assertEquals(
          "the retried addCategory must return the ordinal the taxonomy has for the category"
              + " (taxonomy size "
              + reader.getSize()
              + ", ordinal 13 is "
              + reader.getPath(13)
              + ", ordinal "
              + first
              + " is "
              + (first < reader.getSize() ? reader.getPath(first) : "?")
              + ")",
          onDisk,
          first);
      assertEquals(first, second);
    } finally {
      tw.close();
      dir.close();
    }
  }

  /**
   * After the failed refresh the writer's reader still predates the evicted categories. A cache
   * fill from that reader (here on the first miss, setCacheMissesUntilFill(0)) then declares the
   * cache complete although it holds neither "a" nor the root.
   */
  public void testEvictionRefreshFailureThenFillFromStaleReader() throws Exception {
    AtomicBoolean armed = new AtomicBoolean(false);
    MockDirectoryWrapper dir = failOnceWhileOpeningNrtReaderIn("refreshReaderManager", armed);
    DirectoryTaxonomyWriter tw =
        new DirectoryTaxonomyWriter(
            dir,
            org.apache.lucene.index.IndexWriterConfig.OpenMode.CREATE,
            new org.apache.lucene.facet.taxonomy.writercache.LruTaxonomyWriterCache(3));
    tw.setCacheMissesUntilFill(0);
    int a = tw.addCategory(new FacetLabel("a"));
    tw.addCategory(new FacetLabel("b"));
    armed.set(true);
    // the 4th cache entry evicts root and "a"; the refresh that follows fails once
    expectThrows(IOException.class, () -> tw.addCategory(new FacetLabel("c")));
    assertFalse("the injected failure must have fired", armed.get());
    int again = tw.addCategory(new FacetLabel("a"));
    tw.close();
    dir.close();
    assertEquals("addCategory of an existing category must return its existing ordinal", a, again);
  }
}
