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

import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFloatValueSourceRollupCounts extends LuceneTestCase {

  public void testRolledUpCountOfIntermediateLabel() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("d", true);
    try (Directory dir = new ByteBuffersDirectory();
        Directory taxoDir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
        DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
      Document doc = new Document();
      doc.add(new FacetField("d", "a", "x"));
      w.addDocument(config.build(tw, doc));
      try (DirectoryReader r = DirectoryReader.open(w);
          TaxonomyReader tr = new DirectoryTaxonomyReader(tw)) {
        FacetsCollector fc =
            new IndexSearcher(r).search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
        TaxonomyFacetFloatAssociations sums =
            new TaxonomyFacetFloatAssociations(
                tr, config, fc, AssociationAggregationFunction.SUM, DoubleValuesSource.constant(1));
        // one document under d/a: expected count 1, actual 2 (the sub-tree is rolled up twice)
        assertEquals("count of d/a", 1, sums.getTopChildren(10, "d").labelValues[0].count);
      }
    }
  }
}
