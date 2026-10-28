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
package org.apache.lucene.facet.rangeonrange;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleRangeDocValuesField;
import org.apache.lucene.document.LongRangeDocValuesField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.RangeFieldQuery;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.range.LongRangeFacetCounts;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The range-on-range facet counters take their ranges as varargs, like the classic range counters, which accept
 * an empty list (LongRangeCounter / ExclusiveLongRangeCounter handle "no ranges were requested" explicitly) and
 * return an empty result. LongRangeOnRangeFacetCounts and DoubleRangeOnRangeFacetCounts instead read ranges[0]
 * (getEncodedRanges) and throw ArrayIndexOutOfBoundsException from the constructor.
 *
 * <p>Oracle: an empty result, as the classic counter gives, or an explicit IllegalArgumentException.
 */
public class TestRangeOnRangeFacetCountsNoRanges extends LuceneTestCase {

  public void testNoRanges() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("n", 5));
        doc.add(new LongRangeDocValuesField("lr", new long[] {1}, new long[] {10}));
        doc.add(new DoubleRangeDocValuesField("dr", new double[] {1}, new double[] {10}));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        FacetsCollector fc =
            new IndexSearcher(r).search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        // the classic range counter: zero ranges give an empty result
        FacetResult classic = new LongRangeFacetCounts("n", fc).getAllChildren("n");
        assertEquals(0, classic.childCount);
        assertEquals(0, classic.labelValues.length);

        // Accepted outcomes: an empty result (as above) or an IllegalArgumentException that rejects the
        // empty list explicitly; an internal exception escaping the constructor is not.
        try {
          FacetResult longRor =
              new LongRangeOnRangeFacetCounts("lr", fc, RangeFieldQuery.QueryType.INTERSECTS)
                  .getAllChildren("lr");
          assertEquals(0, longRor.childCount);
          assertEquals(0, longRor.labelValues.length);
        } catch (IllegalArgumentException expectedRejection) {
          // fine
        }
        try {
          FacetResult doubleRor =
              new DoubleRangeOnRangeFacetCounts("dr", fc, RangeFieldQuery.QueryType.INTERSECTS)
                  .getAllChildren("dr");
          assertEquals(0, doubleRor.childCount);
          assertEquals(0, doubleRor.labelValues.length);
        } catch (IllegalArgumentException expectedRejection) {
          // fine
        }
      }
    }
  }
}
