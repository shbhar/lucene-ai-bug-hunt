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
package org.apache.lucene.facet.range;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DynamicRangeInfo.centroid is documented as "the average value in the range", but
 * computeDynamicNumericRanges sums the values of a range in a long, which overflows once the sum
 * passes Long.MAX_VALUE (about 9.2e18). Six nanosecond timestamps of today (about 1.76e18 each)
 * are enough: the centroid comes out negative, outside [min, max].
 */
public class TestDynamicRangeCentroidOverflow extends LuceneTestCase {

  private static final long NANOS_2025 = 1_761_480_000_000_000_000L; // 2025-10-26 in ns

  public void testComputeDynamicRangesNanosecondTimestamps() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 6; i++) {
          Document doc = new Document();
          doc.add(new NumericDocValuesField("ts", NANOS_2025 + i * 1_000_000_000L)); // 1 s apart
          w.addDocument(doc);
        }
      }
      ExecutorService exec = Executors.newSingleThreadExecutor();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        FacetsCollector fc =
            new IndexSearcher(r).search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
        List<DynamicRangeUtil.DynamicRangeInfo> ranges =
            DynamicRangeUtil.computeDynamicRanges(
                "weight",
                LongValuesSource.constant(1),
                LongValuesSource.fromLongField("ts"),
                fc,
                1,
                exec);
        assertEquals(1, ranges.size());
        DynamicRangeUtil.DynamicRangeInfo range = ranges.get(0);
        assertEquals(6, range.count());
        assertEquals(NANOS_2025, range.min());
        assertEquals(NANOS_2025 + 5_000_000_000L, range.max());
        double expectedCentroid = NANOS_2025 + 2_500_000_000.0;
        assertEquals(
            "centroid (the average value in the range) of " + range,
            expectedCentroid,
            range.centroid(),
            1e6);
      } finally {
        exec.shutdown();
      }
    }
  }

  public void testComputeDynamicNumericRanges() {
    long v = 4_000_000_000_000_000_000L; // 4e18 < Long.MAX_VALUE
    long[] values = {v, v, v};
    long[] weights = {1, 1, 1};
    List<DynamicRangeUtil.DynamicRangeInfo> ranges =
        DynamicRangeUtil.computeDynamicNumericRanges(values, weights, 3, 3, 1);
    assertEquals(1, ranges.size());
    DynamicRangeUtil.DynamicRangeInfo range = ranges.get(0);
    assertEquals(v, range.min());
    assertEquals(v, range.max());
    assertTrue(
        "centroid must lie in [min, max]: " + range,
        range.centroid() >= range.min() && range.centroid() <= range.max());
  }
}
