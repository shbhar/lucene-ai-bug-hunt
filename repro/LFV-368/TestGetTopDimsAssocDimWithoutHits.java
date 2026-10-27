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
package org.apache.lucene.facet;

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.taxonomy.AssociationAggregationFunction;
import org.apache.lucene.facet.taxonomy.IntAssociationFacetField;
import org.apache.lucene.facet.taxonomy.TaxonomyFacetIntAssociations;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * Association facets cannot use requireDimCount (FacetsConfig rejects it), so a multi-valued
 * association dim always takes the -1 placeholder path in getTopDims. Checks getTopDims against
 * getAllDims when one such dim has no hits for the query.
 */
public class TestGetTopDimsAssocDimWithoutHits extends LuceneTestCase {

  public void testRequireDimCountRejectedForAssociations() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setMultiValued("a", true);
    config.setRequireDimCount("a", true);
    Directory taxoDir = newDirectory();
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir);
    Document doc = new Document();
    doc.add(new IntAssociationFacetField(1, "a", "a1"));
    expectThrows(IllegalArgumentException.class, () -> config.build(tw, doc));
    IOUtils.close(tw, taxoDir);
  }

  public void testIntAssociations() throws Exception {
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir);
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    FacetsConfig config = new FacetsConfig();
    config.setMultiValued("a", true);
    config.setMultiValued("b", true);
    Document doc = new Document();
    doc.add(new StringField("id", "0", Field.Store.NO));
    doc.add(new IntAssociationFacetField(5, "a", "a1"));
    w.addDocument(config.build(tw, doc));
    doc = new Document();
    doc.add(new StringField("id", "1", Field.Store.NO));
    doc.add(new IntAssociationFacetField(7, "b", "b1"));
    w.addDocument(config.build(tw, doc));
    DirectoryReader r = DirectoryReader.open(w);
    w.close();
    DirectoryTaxonomyReader tr = new DirectoryTaxonomyReader(tw);
    FacetsCollector fc =
        new IndexSearcher(r).search(new TermQuery(new Term("id", "1")), new FacetsCollectorManager());
    Facets facets =
        new TaxonomyFacetIntAssociations(
            FacetsConfig.DEFAULT_INDEX_FIELD_NAME,
            tr,
            config,
            fc,
            AssociationAggregationFunction.SUM);
    List<FacetResult> all = facets.getAllDims(10);
    assertEquals(1, all.size());
    assertEquals("b", all.get(0).dim);
    List<FacetResult> top = facets.getTopDims(10, 10);
    assertFalse("getTopDims returned a null element: " + top, top.contains(null));
    assertEquals(all, top);
    IOUtils.close(r, tr, tw, dir, taxoDir);
  }
}
