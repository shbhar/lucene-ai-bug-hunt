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

import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetResult;
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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFloatFacetTopChildrenNaN extends LuceneTestCase {

  public void testNaNLabelDoesNotDisplaceHigherValue() throws Exception {
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      FacetsConfig config = new FacetsConfig();
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
          DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
        String[] labels = {"a", "b", "c"};
        float[] values = {5f, Float.NaN, 10f};
        for (int i = 0; i < labels.length; i++) {
          Document doc = new Document();
          doc.add(new FloatAssociationFacetField(values[i], "dim", labels[i]));
          w.addDocument(config.build(tw, doc));
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir);
          DirectoryTaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
        FacetsCollector fc =
            new IndexSearcher(r).search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        FacetResult top1 =
            new TaxonomyFacetFloatAssociations(tr, config, fc, AssociationAggregationFunction.SUM)
                .getTopChildren(1, "dim");
        String got = top1.labelValues[0].label;
        // expected c (10.0 outranks a's 5.0) or b (if NaN ranks first); actual a (5.0)
        assertTrue(
            "expected top-1 c or b, but was " + Arrays.toString(top1.labelValues),
            got.equals("c") || got.equals("b"));
      }
    }
  }
}
