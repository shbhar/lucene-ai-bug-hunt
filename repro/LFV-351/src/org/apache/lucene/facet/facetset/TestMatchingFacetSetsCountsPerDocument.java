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
import org.apache.lucene.util.IOUtils;

/**
 * lucene/facet/docs/FacetSets.adoc ("MatchingFacetSetCounts"): "A Facets implementation which
 * counts how many documents are matched by each of a list of FacetSetMatcher". The implementation
 * counts matching facet sets instead, so one document with two facet sets inside a range is counted
 * twice for that range (and the per-matcher count can exceed the number of matching documents).
 */
public class TestMatchingFacetSetsCountsPerDocument extends LuceneTestCase {

  public void testOneDocumentTwoMatchingSets() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    // One actor (document) with two (genre, year) facet sets: Thriller=1 in 2020 and 2021
    Document doc = new Document();
    doc.add(FacetSetsField.create("genreYear", new IntFacetSet(1, 2020), new IntFacetSet(1, 2021)));
    w.addDocument(doc);
    DirectoryReader r = DirectoryReader.open(w);
    w.close();
    FacetsCollector fc =
        new IndexSearcher(r).search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());

    FacetResult result =
        new MatchingFacetSetsCounts(
                "genreYear",
                fc,
                FacetSetDecoder::decodeInts,
                new RangeFacetSetMatcher(
                    "Thriller 2020-2022", new DimRange(1, 1), new DimRange(2020, 2022)))
            .getTopChildren(10, "genreYear");

    assertEquals("documents matched by any matcher", 1, result.value.intValue());
    assertEquals(
        "documents matched by the 'Thriller 2020-2022' matcher (FacetSets.adoc)",
        1,
        result.labelValues[0].value.intValue());
    IOUtils.close(r, dir);
  }
}
