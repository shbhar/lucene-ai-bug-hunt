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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Dim "a" has one hit whose association is 0, dim "b" one hit with association 1. */
public class TestGetTopDimsZeroValuedDim extends LuceneTestCase {

  private void check(boolean useInt) throws Exception {
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      FacetsConfig config = new FacetsConfig();
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())));
          DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
        String[] dims = {"a", "b"};
        for (int i = 0; i < 2; i++) {
          Document doc = new Document();
          doc.add(
              useInt
                  ? new IntAssociationFacetField(i, dims[i], "x")
                  : new FloatAssociationFacetField(i, dims[i], "x"));
          w.addDocument(config.build(tw, doc));
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir);
          DirectoryTaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
        FacetsCollector fc =
            new IndexSearcher(r).search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        Facets facets =
            useInt
                ? new TaxonomyFacetIntAssociations(tr, config, fc, AssociationAggregationFunction.SUM)
                : new TaxonomyFacetFloatAssociations(
                    tr, config, fc, AssociationAggregationFunction.SUM);
        List<String> all = new ArrayList<>(), top = new ArrayList<>();
        for (FacetResult fr : facets.getAllDims(10)) all.add(fr.dim + "=" + fr.value);
        for (FacetResult fr : facets.getTopDims(2, 10)) top.add(fr.dim + "=" + fr.value);
        // Facets#getTopDims: "Results should be the same as calling getAllDims and then only using
        // the first topNDims."
        assertEquals(all, top);
      }
    }
  }

  public void testFloatAssociation() throws Exception {
    check(false);
  }

  public void testIntAssociation() throws Exception {
    check(true);
  }
}
