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
package org.apache.lucene.facet.facetset;

import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDimRangeExclusiveZeroBound extends LuceneTestCase {

  // Expected: -0.0 is counted by exactly one of [-1, 0) and [0, 1]; actual: by neither (sum 0).
  public void testNegativeZeroIsInOneOfTwoAdjacentRanges() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      Document doc = new Document();
      doc.add(FacetSetsField.create("f", new DoubleFacetSet(-0.0)));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        FacetsCollector fc =
            new IndexSearcher(r).search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
        FacetResult res =
            new MatchingFacetSetsCounts(
                    "f",
                    fc,
                    FacetSetDecoder::decodeLongs,
                    new RangeFacetSetMatcher("[-1, 0)", DimRange.fromDoubles(-1, true, 0, false)),
                    new RangeFacetSetMatcher("[0, 1]", DimRange.fromDoubles(0, true, 1, true)))
                .getAllChildren("f");
        assertEquals(
            "[-1, 0) + [0, 1]",
            1,
            res.labelValues[0].value.intValue() + res.labelValues[1].value.intValue());
      }
    }
  }
}
