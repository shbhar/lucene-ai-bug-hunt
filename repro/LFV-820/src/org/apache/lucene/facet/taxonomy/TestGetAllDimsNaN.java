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

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestGetAllDimsNaN extends LuceneTestCase {

  public void testGetAllDimsOrdersNonNaNDims() throws Exception {
    FacetsConfig config = new FacetsConfig();
    try (Directory dir = new ByteBuffersDirectory();
        Directory taxoDir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
          DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
        String[] dims = {"a", "b", "c"};
        float[] values = {1f, Float.NaN, 2f};
        for (int i = 0; i < dims.length; i++) {
          Document doc = new Document();
          doc.add(new FloatAssociationFacetField(values[i], dims[i], "x"));
          w.addDocument(config.build(tw, doc));
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir);
          DirectoryTaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
        FacetsCollector fc =
            new IndexSearcher(r).search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        Facets facets =
            new TaxonomyFacetFloatAssociations(tr, config, fc, AssociationAggregationFunction.SUM);
        List<String> all =
            facets.getAllDims(10).stream().map(fr -> fr.dim + "=" + fr.value).toList();
        // expected: c=2.0 ranked before a=1.0 (getAllDims sorts by value); actual: a=1.0 first
        assertTrue(
            "expected c=2.0 before a=1.0, got " + all,
            all.indexOf("c=2.0") < all.indexOf("a=1.0"));
      }
    }
  }
}
