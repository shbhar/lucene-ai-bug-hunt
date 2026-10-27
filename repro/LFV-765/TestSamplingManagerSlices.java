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
import java.util.function.Function;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.IndexSearcher.LeafSlice;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.NamedThreadFactory;

/**
 * {@link RandomSamplingFacetsCollector#createManager} on a searcher with several slices must sample
 * the hits once, as it does on a searcher with one slice. Every document has the label color/red,
 * so the true count of that label is the number of hits.
 */
public class TestSamplingManagerSlices extends LuceneTestCase {

  private static final String DIM = "color";
  private static final FacetsConfig CONFIG = new FacetsConfig();

  /** Default slicing: 10 segments of 2,000 documents are searched as 2 slices of 5 segments. */
  public void testDefaultSlicing() throws Exception {
    check(10, 2_000, 1_000, null);
  }

  /** The smallest case: 2 segments of 1,024 documents, one slice per segment. */
  public void testOneSlicePerSegment() throws Exception {
    check(2, 1_024, 128, leaves -> IndexSearcher.slices(leaves, 1, 1, false));
  }

  /** Intra-segment concurrency: one segment of 65,536 documents searched as 4 partitions. */
  public void testSegmentPartitions() throws Exception {
    check(1, 1 << 16, 1_024, leaves -> IndexSearcher.slices(leaves, 1 << 14, 5, true));
  }

  /** What one sampled search returned. */
  private record Sample(
      int slices, int originalHits, int sampledHits, double samplingRate, long amortized) {
    @Override
    public String toString() {
      return slices
          + (slices == 1 ? " slice" : " slices")
          + ": original hits "
          + originalHits
          + ", sampled "
          + sampledHits
          + ", getSamplingRate "
          + samplingRate
          + ", amortized count "
          + amortized;
    }
  }

  private void check(
      int numSegments,
      int docsPerSegment,
      int sampleSize,
      Function<List<LeafReaderContext>, LeafSlice[]> slicer)
      throws IOException {
    int numDocs = numSegments * docsPerSegment;
    ExecutorService executor =
        Executors.newFixedThreadPool(4, new NamedThreadFactory("TestSamplingManagerSlices"));
    try (Directory dir = index(numSegments, docsPerSegment);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(numSegments, reader.leaves().size());
      IndexSearcher oneSlice = new IndexSearcher(reader); // no executor: one slice
      IndexSearcher manySlices =
          slicer == null
              ? new IndexSearcher(reader, executor)
              : new IndexSearcher(reader, executor) {
                @Override
                protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
                  return slicer.apply(leaves);
                }
              };

      Sample expected = sample(oneSlice, sampleSize);
      Sample actual = sample(manySlices, sampleSize);
      String msg =
          "true count "
              + numDocs
              + ", sampleSize "
              + sampleSize
              + " | "
              + expected
              + " | "
              + actual;
      if (VERBOSE) {
        System.out.println(msg);
      }

      // Control: one slice samples sampleSize hits and amortizes them to the true count.
      assertEquals(msg, 1, expected.slices());
      assertEquals(msg, numDocs, expected.originalHits());
      assertEquals(msg, sampleSize, expected.sampledHits());
      assertEquals(msg, numDocs, expected.amortized());

      // Several slices: about the same sample and amortized count, and the same original hits.
      assertTrue(msg, actual.slices() > 1);
      assertTrue(msg, actual.amortized() >= numDocs / 2 && actual.amortized() <= 2L * numDocs);
      assertTrue(
          msg, actual.sampledHits() >= sampleSize / 2 && actual.sampledHits() <= 2 * sampleSize);
      assertEquals(msg, numDocs, actual.originalHits());
    } finally {
      TestUtil.shutdownExecutorService(executor);
    }
  }

  /** {@code numSegments} segments of {@code docsPerSegment} documents, all labelled color/red. */
  private static Directory index(int numSegments, int docsPerSegment) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE).setRAMBufferSizeMB(256);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int s = 0; s < numSegments; s++) {
        for (int i = 0; i < docsPerSegment; i++) {
          Document doc = new Document();
          doc.add(new SortedSetDocValuesFacetField(DIM, "red"));
          w.addDocument(CONFIG.build(doc));
        }
        w.commit(); // one segment each
      }
    }
    return dir;
  }

  private static Sample sample(IndexSearcher searcher, int sampleSize) throws IOException {
    RandomSamplingFacetsCollector fc =
        searcher.search(
            MatchAllDocsQuery.INSTANCE,
            RandomSamplingFacetsCollector.createManager(sampleSize, 42L));
    SortedSetDocValuesReaderState state =
        new DefaultSortedSetDocValuesReaderState(searcher.getIndexReader(), CONFIG);
    FacetResult counts = new SortedSetDocValuesFacetCounts(state, fc).getTopChildren(10, DIM);
    FacetResult amortized = fc.amortizeFacetCounts(counts, CONFIG, searcher);
    assertEquals(1, amortized.labelValues.length);
    return new Sample(
        searcher.getSlices().length,
        countHits(fc.getOriginalMatchingDocs()),
        countHits(fc.getMatchingDocs()),
        fc.getSamplingRate(),
        amortized.labelValues[0].value.longValue());
  }

  private static int countHits(List<FacetsCollector.MatchingDocs> matchingDocs) throws IOException {
    int n = 0;
    for (FacetsCollector.MatchingDocs md : matchingDocs) {
      DocIdSetIterator it = md.bits().iterator();
      while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
        n++;
      }
    }
    return n;
  }
}
