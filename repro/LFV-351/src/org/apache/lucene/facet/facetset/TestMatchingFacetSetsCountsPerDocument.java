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
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMatchingFacetSetsCountsPerDocument extends LuceneTestCase {

  public void testOneDocumentTwoMatchingSets() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(
            FacetSetsField.create("genreYear", new IntFacetSet(1, 2020), new IntFacetSet(1, 2021)));
        writer.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        FacetsCollector fc =
            new IndexSearcher(reader)
                .search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
        FacetResult result =
            new MatchingFacetSetsCounts(
                    "genreYear",
                    fc,
                    FacetSetDecoder::decodeInts,
                    new RangeFacetSetMatcher(
                        "Thriller 2020-2022", new DimRange(1, 1), new DimRange(2020, 2022)))
                .getTopChildren(10, "genreYear");
        // FacetSets.adoc: each matcher counts the documents it matches; 1 document, counted 2
        assertEquals(
            "documents matched by 'Thriller 2020-2022'", 1, result.labelValues[0].value.intValue());
      }
    }
  }
}
