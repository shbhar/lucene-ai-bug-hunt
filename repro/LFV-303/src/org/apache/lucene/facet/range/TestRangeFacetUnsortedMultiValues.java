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

import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.MultiLongValues;
import org.apache.lucene.facet.MultiLongValuesSource;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestRangeFacetUnsortedMultiValues extends LuceneTestCase {

  public void testValuesNotInAscendingOrder() throws Exception {
    MultiLongValuesSource source =
        new MultiLongValuesSource() {
          @Override
          public MultiLongValues getValues(LeafReaderContext ctx) {
            return new MultiLongValues() {
              final long[] values = {30, 5};
              int upto;

              @Override
              public boolean advanceExact(int doc) {
                upto = 0;
                return true;
              }

              @Override
              public long getValueCount() {
                return values.length;
              }

              @Override
              public long nextValue() {
                return values[upto++];
              }
            };
          }

          @Override
          public boolean isCacheable(LeafReaderContext ctx) {
            return false;
          }

          @Override
          public int hashCode() {
            return 0;
          }

          @Override
          public boolean equals(Object o) {
            return o == this;
          }

          @Override
          public String toString() {
            return "{30, 5}";
          }
        };
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(new Document());
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        FacetsCollector fc =
            new IndexSearcher(r).search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
        LongRange range = new LongRange("[0, 10)", 0, true, 10, false);
        LongRangeFacetCounts facets = new LongRangeFacetCounts("x", source, fc, range);
        // expected 1 (value 5 is in [0, 10), as range.getQuery(null, source) agrees), actual 0
        assertEquals(1, facets.getAllChildren("x").labelValues[0].value.intValue());
      }
    }
  }
}
