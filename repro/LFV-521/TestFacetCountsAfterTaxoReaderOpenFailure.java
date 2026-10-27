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
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * End-user view of the candidate: an application indexes documents with FacetsConfig.build and a
 * default DirectoryTaxonomyWriter. One build() call fails with an IOException because a segment
 * file could not be opened while the taxonomy writer opened its near-real-time reader (as a failed
 * memory map would). The application skips that document and keeps indexing. A later document with
 * an existing value must be counted under that value.
 *
 * <p>The failure is injected with a plain FilterDirectory, only in openInput calls made while a
 * near-real-time reader is being opened (not during flush or compound-file creation).
 */
public class TestFacetCountsAfterTaxoReaderOpenFailure extends LuceneTestCase {

  private static boolean openingNrtReader() {
    return StackWalker.getInstance()
        .walk(
            s ->
                s.anyMatch(
                    f ->
                        f.getClassName().endsWith(".StandardDirectoryReader")
                            && f.getMethodName().equals("open")));
  }

  /** The candidate: one injected failure, then an existing value is re-added. */
  public void testSkippedDocumentThenExistingValueIsCounted() throws Exception {
    run(true);
  }

  /** Control: the same sequence without the injected failure (the skipped document is indexed). */
  public void testControlWithoutFailure() throws Exception {
    run(false);
  }

  private void run(boolean inject) throws Exception {
    AtomicBoolean armed = new AtomicBoolean(false);
    Directory taxoDir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexInput openInput(String name, IOContext context) throws IOException {
            if (armed.get() && openingNrtReader()) {
              armed.set(false);
              throw new IOException("simulated: could not map segment file " + name);
            }
            return super.openInput(name, context);
          }
        };
    Directory indexDir = newDirectory();
    IndexWriter iw = new IndexWriter(indexDir, newIndexWriterConfig());
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir); // default cache (4000)
    FacetsConfig config = new FacetsConfig();

    // root + "dim" + 3998 values = 4000 cache entries
    for (int i = 0; i < 3998; i++) {
      Document d = new Document();
      d.add(new FacetField("dim", "v" + i));
      iw.addDocument(config.build(tw, d));
    }

    // the next new value fills the cache past its limit; the writer refreshes its reader and the
    // open of a segment file fails once. The application skips this document.
    Document bad = new Document();
    bad.add(new FacetField("dim", "v3998"));
    if (inject) {
      armed.set(true);
      expectThrows(IOException.class, () -> config.build(tw, bad));
      assertFalse("the injected failure must have fired", armed.get());
    } else {
      iw.addDocument(config.build(tw, bad));
    }

    // the application keeps indexing: one more document with the existing value v0
    Document d = new Document();
    d.add(new FacetField("dim", "v0"));
    iw.addDocument(config.build(tw, d));

    // commit taxonomy first, then the index, as documented
    tw.commit();
    iw.commit();
    int taxoSize = tw.getSize();
    tw.close();
    iw.close();

    try (DirectoryReader r = DirectoryReader.open(indexDir);
        DirectoryTaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
      IndexSearcher s = new IndexSearcher(r);
      FacetsCollector fc = s.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
      Facets f = new FastTaxonomyFacetCounts(tr, config, fc);
      Number v0 = f.getSpecificValue("dim", "v0");
      assertEquals(
          "two documents carry dim/v0 (taxonomy size "
              + taxoSize
              + "; ordinal of 'dim' = "
              + tr.getOrdinal("dim")
              + ", path at ordinal "
              + (taxoSize - 2)
              + " = "
              + (taxoSize >= 2 ? tr.getPath(taxoSize - 2) : "?")
              + ", path at ordinal "
              + (taxoSize - 1)
              + " = "
              + (taxoSize >= 1 ? tr.getPath(taxoSize - 1) : "?")
              + ")",
          2,
          v0.intValue());
    } finally {
      indexDir.close();
      taxoDir.close();
    }
  }
}
