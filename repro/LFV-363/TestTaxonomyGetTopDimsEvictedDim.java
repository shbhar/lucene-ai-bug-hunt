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
package org.apache.lucene.facet.taxonomy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Facets#getTopDims: "Results should be the same as calling getAllDims and then only using the
 * first topNDims." Taxonomy facets visit dims newest-created first. When a multi-valued dim that is
 * visited later takes over the bottom slot of the top-dims queue, its result is built from the
 * ordinal of the dim it pushed out.
 */
public class TestTaxonomyGetTopDimsEvictedDim extends LuceneTestCase {

  /** x is created first, so it is visited after y; x has 2 hits, y has 1. */
  public void testOlderDimWithMoreHits() throws Exception {
    assertTopDimsIsPrefixOfAllDims(1, "x/x1", "x/x2", "y/y1");
  }

  /** a (3 hits), then b (2 hits), then c (1 hit) are created; ask for the top 2 dims. */
  public void testTopTwoOfThree() throws Exception {
    assertTopDimsIsPrefixOfAllDims(2, "a/a1", "a/a2", "a/a3", "b/b1", "b/b2", "c/c1");
  }

  /** Control: the data of testOlderDimWithMoreHits, but y is created first. Passes. */
  public void testControlWinnerVisitedFirst() throws Exception {
    assertTopDimsIsPrefixOfAllDims(1, "y/y1", "x/x1", "x/x2");
  }

  /**
   * Indexes one document per "dim/label", in the given order. Every dim is multi-valued and
   * requires dim counts, as the getTopDims javadoc advises.
   */
  private void assertTopDimsIsPrefixOfAllDims(int topNDims, String... docs) throws IOException {
    FacetsConfig config = new FacetsConfig();
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory();
        DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String doc : docs) {
        String[] dimAndLabel = doc.split("/");
        config.setMultiValued(dimAndLabel[0], true);
        config.setRequireDimCount(dimAndLabel[0], true);
        Document d = new Document();
        d.add(new FacetField(dimAndLabel[0], dimAndLabel[1]));
        writer.addDocument(config.build(taxoWriter, d));
      }
      try (DirectoryReader reader = DirectoryReader.open(writer);
          DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter)) {
        FacetsCollector hits =
            newSearcher(reader).search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
        Facets facets = new FastTaxonomyFacetCounts(taxoReader, config, hits);
        List<FacetResult> allDims = facets.getAllDims(10);
        assertEquals(
            "getTopDims(" + topNDims + ", 10) vs getAllDims(10).subList(0, " + topNDims + ")",
            render(allDims.subList(0, topNDims)),
            render(facets.getTopDims(topNDims, 10)));
      }
    }
  }

  /** FacetResult#equals ignores dim and path, so compare dim, value, childCount and children. */
  private static List<String> render(List<FacetResult> results) {
    List<String> out = new ArrayList<>();
    for (FacetResult r : results) {
      out.add(
          r == null
              ? "null"
              : r.dim
                  + " value="
                  + r.value
                  + " childCount="
                  + r.childCount
                  + " "
                  + Arrays.toString(r.labelValues));
    }
    return out;
  }
}
