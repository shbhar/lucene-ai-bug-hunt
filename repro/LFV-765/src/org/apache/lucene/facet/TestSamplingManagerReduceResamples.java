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
package org.apache.lucene.facet;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * RandomSamplingFacetsCollector.createManager: with more than one slice, the reduced collector
 * samples the hits that the per-slice collectors already sampled, so the sample (and every
 * amortized count) is far smaller than with the sequential collector on the same index.
 *
 * <p>Every document has the facet label color/red, so the exact count is the number of hits. The
 * sequential collector samples sampleSize hits and amortizes them back to the hit count. The
 * collector manager, run on a searcher whose slices each hold one segment, returns a sample of
 * about sampleSize^2 * numSlices / totalHits hits, and an amortized count that is low by the
 * per-slice sampling ratio.
 */
public class TestSamplingManagerReduceResamples extends LuceneTestCase {

  private static final String DIM = "color";

  /** Two segments of {@code docsPerSegment} documents, every one labelled color/red. */
  private static Directory buildIndex(FacetsConfig config, int docsPerSegment) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setRAMBufferSizeMB(256)
            .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int seg = 0; seg < 2; seg++) {
        for (int i = 0; i < docsPerSegment; i++) {
          Document doc = new Document();
          doc.add(new SortedSetDocValuesFacetField(DIM, "red"));
          w.addDocument(config.build(doc));
        }
        w.commit();
      }
    }
    return dir;
  }

  private static int sampledDocCount(FacetsCollector fc) throws IOException {
    int n = 0;
    for (FacetsCollector.MatchingDocs md : fc.getMatchingDocs()) {
      DocIdSetIterator it = md.bits().iterator();
      while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
        n++;
      }
    }
    return n;
  }

  private static long amortizedRedCount(
      RandomSamplingFacetsCollector fc, FacetsConfig config, IndexSearcher searcher)
      throws IOException {
    SortedSetDocValuesReaderState state =
        new DefaultSortedSetDocValuesReaderState(searcher.getIndexReader(), config);
    FacetResult res = new SortedSetDocValuesFacetCounts(state, fc).getTopChildren(10, DIM);
    FacetResult amortized = fc.amortizeFacetCounts(res, config, searcher);
    assertEquals(1, amortized.labelValues.length);
    assertEquals("red", amortized.labelValues[0].label);
    return amortized.labelValues[0].value.longValue();
  }

  private void checkManagerMatchesSequential(
      IndexReader reader, IndexSearcher concurrentSearcher, FacetsConfig config, int sampleSize)
      throws IOException {
    int totalHits = reader.numDocs();
    assertTrue("needs more than one slice", concurrentSearcher.getSlices().length > 1);

    // Sequential: one collector over the whole index.
    IndexSearcher sequentialSearcher = new IndexSearcher(reader);
    CollectorManager<RandomSamplingFacetsCollector, RandomSamplingFacetsCollector> single =
        RandomSamplingFacetsCollector.createManager(sampleSize, 42L);
    RandomSamplingFacetsCollector seq = sequentialSearcher.search(new MatchAllDocsQuery(), single);
    int seqSampled = sampledDocCount(seq);
    long seqAmortized = amortizedRedCount(seq, config, sequentialSearcher);

    // The same manager on a searcher with one slice per segment.
    CollectorManager<RandomSamplingFacetsCollector, RandomSamplingFacetsCollector> manager =
        RandomSamplingFacetsCollector.createManager(sampleSize, 42L);
    RandomSamplingFacetsCollector conc = concurrentSearcher.search(new MatchAllDocsQuery(), manager);
    int concSampled = sampledDocCount(conc);
    long concAmortized = amortizedRedCount(conc, config, concurrentSearcher);

    String msg =
        "totalHits="
            + totalHits
            + " sampleSize="
            + sampleSize
            + " sequential: sampled="
            + seqSampled
            + " rate="
            + seq.getSamplingRate()
            + " amortized="
            + seqAmortized
            + " | manager over "
            + concurrentSearcher.getSlices().length
            + " slices: sampled="
            + concSampled
            + " rate="
            + conc.getSamplingRate()
            + " amortized="
            + concAmortized;
    System.out.println(msg);

    // Controls: the sequential sample has the documented size and amortizes to the exact count.
    assertEquals(msg, sampleSize, seqSampled);
    assertEquals(msg, totalHits, seqAmortized);
    // Both report the documented sampling ratio sampleSize / totalHits.
    assertEquals(msg, (double) sampleSize / totalHits, seq.getSamplingRate(), 0d);
    assertEquals(msg, (double) sampleSize / totalHits, conc.getSamplingRate(), 0d);
    // The concurrent sample must have about the same size (the bins only shift at segment
    // boundaries) and amortize to about the same count.
    assertTrue(msg, Math.abs(concSampled - sampleSize) <= 2);
    assertTrue(msg, concAmortized >= totalHits / 2);
  }

  /** One slice per segment through the documented IndexSearcher#slices hook; small index. */
  public void testOneSlicePerSegment() throws Exception {
    FacetsConfig config = new FacetsConfig();
    // 2 x 1024 hits, sampleSize 128: ratio 1/16 overall, 1/8 per slice (exact in binary).
    try (Directory dir = buildIndex(config, 1024);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(2, reader.leaves().size());
      ExecutorService executor = Executors.newFixedThreadPool(2);
      try {
        IndexSearcher searcher =
            new IndexSearcher(reader, executor) {
              @Override
              protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
                return IndexSearcher.slices(leaves, 1, 1, false);
              }
            };
        checkManagerMatchesSequential(reader, searcher, config, 128);
      } finally {
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
      }
    }
  }

  /** Default slicing: segments above 250,000 documents get a slice of their own. */
  public void testDefaultSlicesLargeSegments() throws Exception {
    FacetsConfig config = new FacetsConfig();
    // 2 x 2^18 hits, sampleSize 2^12: ratio 2^-7 overall, 2^-6 per slice (exact in binary).
    try (Directory dir = buildIndex(config, 1 << 18);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(2, reader.leaves().size());
      ExecutorService executor = Executors.newFixedThreadPool(2);
      try {
        IndexSearcher searcher = new IndexSearcher(reader, executor);
        checkManagerMatchesSequential(reader, searcher, config, 1 << 12);
      } finally {
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
      }
    }
  }
}
