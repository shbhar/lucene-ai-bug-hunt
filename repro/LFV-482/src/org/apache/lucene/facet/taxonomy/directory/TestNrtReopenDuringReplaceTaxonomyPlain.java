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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Plain-threads companion of TestNrtReopenDuringReplaceTaxonomy (no hooks): one thread keeps
 * replacing the taxonomy, alternating between [a, b] and [b, a], while another keeps reopening an
 * NRT reader, filling its caches and checking getPath against the reader's own index content.
 * Timing dependent; use it to measure how often the real scheduler hits the window. Reads the
 * taxonomy's own doc values field through the package-private Consts, as upstream tests do.
 */
public class TestNrtReopenDuringReplaceTaxonomyPlain extends LuceneTestCase {

  private static Directory taxonomy(String... labels) throws Exception {
    Directory dir = newDirectory();
    try (DirectoryTaxonomyWriter w = new DirectoryTaxonomyWriter(dir)) {
      for (String l : labels) {
        w.addCategory(new FacetLabel(l));
      }
    }
    return dir;
  }

  /** The label of ordinal ord as stored in the reader's own index, bypassing every cache. */
  private static String rawLabel(DirectoryTaxonomyReader r, int ord) throws Exception {
    for (LeafReaderContext ctx : r.getInternalIndexReader().leaves()) {
      LeafReader leaf = ctx.reader();
      if (ord >= ctx.docBase && ord < ctx.docBase + leaf.maxDoc()) {
        BinaryDocValues values = leaf.getBinaryDocValues(Consts.FULL);
        assertTrue(values.advanceExact(ord - ctx.docBase));
        return values.binaryValue().utf8ToString();
      }
    }
    return null;
  }

  public void testReopenWhileReplacing() throws Exception {
    int replaces = 400;
    try (Directory ab = taxonomy("a", "b");
        Directory ba = taxonomy("b", "a");
        Directory dir = newDirectory()) {
      DirectoryTaxonomyWriter writer = new DirectoryTaxonomyWriter(dir);
      writer.addCategory(new FacetLabel("a"));
      writer.addCategory(new FacetLabel("b"));
      AtomicBoolean stop = new AtomicBoolean();
      AtomicInteger reopens = new AtomicInteger();
      AtomicInteger mismatches = new AtomicInteger();
      AtomicInteger reopenFailures = new AtomicInteger();
      AtomicReference<String> first = new AtomicReference<>();
      AtomicReference<Throwable> unexpected = new AtomicReference<>();
      DirectoryTaxonomyReader r0 = new DirectoryTaxonomyReader(writer);
      Thread reader =
          new Thread(
              () -> {
                DirectoryTaxonomyReader r = r0;
                try {
                  while (stop.get() == false) {
                    DirectoryTaxonomyReader nr;
                    try {
                      nr = TaxonomyReader.openIfChanged(r);
                    } catch (ArrayIndexOutOfBoundsException e) {
                      reopenFailures.incrementAndGet();
                      first.compareAndSet(null, "openIfChanged threw " + e);
                      continue;
                    }
                    if (nr == null) {
                      continue;
                    }
                    r.close();
                    r = nr;
                    reopens.incrementAndGet();
                    if (r.getInternalIndexReader().maxDoc() != 3) {
                      continue; // a reader opened between deleteAll and addIndexes
                    }
                    r.getParallelTaxonomyArrays();
                    for (int ord = 1; ord <= 2; ord++) {
                      String cached = r.getPath(ord).components[0];
                      String raw = rawLabel(r, ord);
                      if (cached.equals(raw) == false) {
                        mismatches.incrementAndGet();
                        first.compareAndSet(
                            null, "ordinal " + ord + ": getPath=" + cached + " index=" + raw);
                      }
                    }
                  }
                } catch (Throwable t) {
                  unexpected.set(t);
                } finally {
                  try {
                    r.close();
                  } catch (Exception e) {
                    unexpected.compareAndSet(null, e);
                  }
                }
              });
      reader.start();
      try {
        for (int i = 0; i < replaces; i++) {
          writer.replaceTaxonomy(i % 2 == 0 ? ba : ab);
        }
      } finally {
        stop.set(true);
        reader.join();
        writer.close();
      }
      assertNull(unexpected.get());
      assertEquals(
          "replaces="
              + replaces
              + " reopens="
              + reopens.get()
              + " readers answering from another taxonomy's caches="
              + mismatches.get()
              + " reopen failures="
              + reopenFailures.get()
              + "; first: "
              + first.get(),
          0,
          mismatches.get() + reopenFailures.get());
    }
  }
}
