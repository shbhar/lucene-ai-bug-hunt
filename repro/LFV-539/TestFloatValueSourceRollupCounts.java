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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * TaxonomyFacetFloatAssociations with a DoubleValuesSource on a hierarchical dimension reports a
 * LabelAndValue#count that is too high for every label that has children: the counts of the
 * sub-tree are rolled up more than once. FastTaxonomyFacetCounts over the same hits gives the
 * right count.
 */
public class TestFloatValueSourceRollupCounts extends LuceneTestCase {

  public void testRolledUpCountOfIntermediateLabel() throws Exception {
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir);
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("d", true);

    Document doc = new Document();
    doc.add(new FacetField("d", "a", "x"));
    doc.add(new NumericDocValuesField("w", 7));
    w.addDocument(config.build(tw, doc));

    doc = new Document();
    doc.add(new FacetField("d", "a", "y"));
    doc.add(new NumericDocValuesField("w", 2));
    w.addDocument(config.build(tw, doc));

    DirectoryReader r = DirectoryReader.open(w);
    TaxonomyReader tr = new DirectoryTaxonomyReader(tw);
    IndexSearcher searcher = newSearcher(r);
    FacetsCollector fc = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
    try {
    Facets counts = new FastTaxonomyFacetCounts(tr, config, fc);
    Facets sums =
        new TaxonomyFacetFloatAssociations(
            tr, config, fc, AssociationAggregationFunction.SUM, DoubleValuesSource.fromLongField("w"));

    FacetResult countResult = counts.getTopChildren(10, "d");
    FacetResult sumResult = sums.getTopChildren(10, "d");
    LabelAndValue countA = countResult.labelValues[0];
    LabelAndValue sumA = sumResult.labelValues[0];
    assertEquals("a", countA.label);
    assertEquals("a", sumA.label);
    // Two documents are under d/a, as FastTaxonomyFacetCounts reports:
    assertEquals(2, countA.count);
    // The aggregated value is right (7 + 2):
    assertEquals(9f, sumA.value.floatValue(), 0f);
    // ... and the count must be the same two documents ("Number of occurrences for this label"):
    assertEquals("getTopChildren count of d/a: " + sumResult, 2, sumA.count);

    FacetResult all = sums.getAllChildren("d");
    assertEquals("getAllChildren count of d/a: " + all, 2, all.labelValues[0].count);
    } finally {
      IOUtils.close(r, tr, w, tw, dir, taxoDir);
    }
  }
}
