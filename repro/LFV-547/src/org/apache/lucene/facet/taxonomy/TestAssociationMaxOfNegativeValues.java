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
 * AssociationAggregationFunction.MAX "computes the maximum value". Non-positive association values
 * are supported (TestTaxonomyFacetAssociations#testNonPositiveAggregations returns -1 for a SUM),
 * but MAX starts from 0, so the maximum of negative values is reported as 0.
 */
public class TestAssociationMaxOfNegativeValues extends LuceneTestCase {

  public void testMaxOfNegativeAssociations() throws Exception {
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir);
    FacetsConfig config = new FacetsConfig();
    config.setIndexFieldName("int", "$int_facets");
    config.setIndexFieldName("float", "$float_facets");

    Document doc = new Document();
    doc.add(new IntAssociationFacetField(-3, "int", "x"));
    doc.add(new FloatAssociationFacetField(-1.5f, "float", "x"));
    w.addDocument(config.build(tw, doc));
    doc = new Document();
    doc.add(new IntAssociationFacetField(-5, "int", "x"));
    doc.add(new FloatAssociationFacetField(-2.5f, "float", "x"));
    w.addDocument(config.build(tw, doc));

    DirectoryReader r = DirectoryReader.open(w);
    TaxonomyReader tr = new DirectoryTaxonomyReader(tw);
    IndexSearcher searcher = newSearcher(r);
    FacetsCollector fc = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
    try {
    Facets intSum =
        new TaxonomyFacetIntAssociations(
            "$int_facets", tr, config, fc, AssociationAggregationFunction.SUM);
    Facets intMax =
        new TaxonomyFacetIntAssociations(
            "$int_facets", tr, config, fc, AssociationAggregationFunction.MAX);
    Facets floatMax =
        new TaxonomyFacetFloatAssociations(
            "$float_facets", tr, config, fc, AssociationAggregationFunction.MAX);

    // control: SUM of the negative values is reported as such
    assertEquals(-8, intSum.getSpecificValue("int", "x").intValue());

    // MAX(-3, -5) = -3 and MAX(-1.5, -2.5) = -1.5:
    FacetResult ir = intMax.getTopChildren(10, "int");
    assertEquals("int MAX: " + ir, -3, ir.labelValues[0].value.intValue());
    assertEquals(-3, intMax.getSpecificValue("int", "x").intValue());
    FacetResult fr = floatMax.getTopChildren(10, "float");
    assertEquals("float MAX: " + fr, -1.5f, fr.labelValues[0].value.floatValue(), 0f);
    assertEquals(-1.5f, floatMax.getSpecificValue("float", "x").floatValue(), 0f);
    } finally {
      IOUtils.close(r, tr, w, tw, dir, taxoDir);
    }
  }

  public void testMaxOfNegativeValueSource() throws Exception {
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir);
    FacetsConfig config = new FacetsConfig();

    Document doc = new Document();
    doc.add(new FacetField("temperature", "station-1"));
    doc.add(new NumericDocValuesField("celsius", -4));
    w.addDocument(config.build(tw, doc));
    doc = new Document();
    doc.add(new FacetField("temperature", "station-1"));
    doc.add(new NumericDocValuesField("celsius", -7));
    w.addDocument(config.build(tw, doc));

    DirectoryReader r = DirectoryReader.open(w);
    TaxonomyReader tr = new DirectoryTaxonomyReader(tw);
    IndexSearcher searcher = newSearcher(r);
    FacetsCollector fc = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
    try {
    Facets max =
        new TaxonomyFacetFloatAssociations(
            tr,
            config,
            fc,
            AssociationAggregationFunction.MAX,
            DoubleValuesSource.fromLongField("celsius"));
    FacetResult res = max.getTopChildren(10, "temperature");
    // MAX(-4, -7) = -4, for the label and for the dimension value of this single-valued dim:
    assertEquals("value-source MAX: " + res, -4f, res.labelValues[0].value.floatValue(), 0f);
    assertEquals("value-source MAX dim value: " + res, -4f, res.value.floatValue(), 0f);
    } finally {
      IOUtils.close(r, tr, w, tw, dir, taxoDir);
    }
  }
}
