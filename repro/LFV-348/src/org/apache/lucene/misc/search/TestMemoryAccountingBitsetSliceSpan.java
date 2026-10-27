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
package org.apache.lucene.misc.search;

import java.util.concurrent.Executor;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.misc.CollectorMemoryTracker;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.FixedBitSet;

/**
 * With the default IndexSearcher slicing, small segments are grouped into one slice even when
 * large segments (searched by other slices) sit between them in doc-id order. The collector of
 * such a slice sizes its bitset to the doc-id span from its first to its last segment, so it
 * allocates, and charges to the memory tracker, bits for every document of the segments in
 * between, which another collector already covers.
 */
public class TestMemoryAccountingBitsetSliceSpan extends LuceneTestCase {

  private static final int SMALL = 10;
  private static final int BIG = 300_000; // > IndexSearcher's default 250,000 docs per slice

  private Directory dir;
  private IndexReader reader;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      // segments in doc-id order: small, big, small, big, small
      for (int size : new int[] {SMALL, BIG, SMALL, BIG, SMALL}) {
        for (int i = 0; i < size; i++) {
          w.addDocument(new Document());
        }
        w.flush();
      }
      w.commit();
    }
    reader = DirectoryReader.open(dir);
    assertEquals(5, reader.leaves().size());
    for (LeafReaderContext ctx : reader.leaves()) {
      assertEquals(ctx.ord % 2 == 0 ? SMALL : BIG, ctx.reader().maxDoc());
    }
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private static long trackedBytes(IndexSearcher searcher, long limit) throws Exception {
    MemoryAccountingBitsetCollectorManager.Result result =
        searcher.search(
            MatchAllDocsQuery.INSTANCE,
            new MemoryAccountingBitsetCollectorManager(new CollectorMemoryTracker("t", limit)));
    assertEquals(3 * SMALL + 2 * BIG, result.bitSet().cardinality());
    return result.totalBytesUsed();
  }

  /** The default slicing: two slices of one big segment each, one slice of the three small ones. */
  private IndexSearcher concurrentSearcher() {
    Executor sameThread = Runnable::run; // any executor enables slicing; no threads needed
    IndexSearcher s = new IndexSearcher(reader, sameThread);
    IndexSearcher.LeafSlice[] slices = s.getSlices();
    assertEquals(3, slices.length);
    return s;
  }

  public void testEachCollectorAllocatesOnlyForTheSegmentsItSearches() throws Exception {
    IndexSearcher concurrent = concurrentSearcher();
    MemoryAccountingBitsetCollectorManager.Result result =
        concurrent.search(
            MatchAllDocsQuery.INSTANCE,
            new MemoryAccountingBitsetCollectorManager(
                new CollectorMemoryTracker("t", Long.MAX_VALUE)));
    // Class javadoc: "each collector only allocates a bitset for the slices it processes". Bound
    // every collector by a bitset over the documents of the segments in its slice, with 25%
    // headroom for ArrayUtil's growth policy plus 128 bytes, and add the result bitset.
    long allowed = result.bitSet().ramBytesUsed();
    StringBuilder perSlice = new StringBuilder();
    for (IndexSearcher.LeafSlice slice : concurrent.getSlices()) {
      int own = 0;
      int min = Integer.MAX_VALUE;
      int max = 0;
      for (IndexSearcher.LeafReaderContextPartition p : slice.partitions) {
        own += p.ctx.reader().maxDoc();
        min = Math.min(min, p.ctx.docBase);
        max = Math.max(max, p.ctx.docBase + p.ctx.reader().maxDoc());
      }
      allowed += (long) (1.25 * new FixedBitSet(own).ramBytesUsed()) + 128;
      perSlice.append(" [docs searched=").append(own).append(", doc-id span=").append(max - min).append(']');
    }
    long sequential = trackedBytes(new IndexSearcher(reader), Long.MAX_VALUE);
    String msg =
        "concurrent search tracked "
            + result.totalBytesUsed()
            + " bytes, allowed "
            + allowed
            + " (sequential search: "
            + sequential
            + " bytes); slices:"
            + perSlice;
    System.out.println(msg);
    assertTrue(msg, result.totalBytesUsed() <= allowed);
  }

  public void testMemoryLimitThatFitsSequentialSearchFitsConcurrentSearch() throws Exception {
    long sequential = trackedBytes(new IndexSearcher(reader), Long.MAX_VALUE);
    // A limit just above what the sequential search tracks. In this layout an implementation that
    // allocates per slice tracks no more than the sequential search does.
    long limit = sequential + 2 * 1024;
    trackedBytes(new IndexSearcher(reader), limit); // fits
    try {
      long concurrent = trackedBytes(concurrentSearcher(), limit);
      System.out.println("limit " + limit + ": sequential " + sequential + ", concurrent " + concurrent);
    } catch (IllegalStateException e) {
      throw new AssertionError(
          "limit "
              + limit
              + " (sequential search uses "
              + sequential
              + " bytes) fails with an executor: "
              + e.getMessage(),
          e);
    }
  }
}
