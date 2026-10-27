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
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * FacetResult#value is "Total number of documents containing a value for this path". For a sub-path
 * of a single-valued hierarchical taxonomy dimension, FastTaxonomyFacetCounts reports the sum of
 * the child counts instead, which leaves out every document labeled with the sub-path itself;
 * SortedSetDocValues faceting, getSpecificValue on the same Facets, and the taxonomy with the
 * dimension configured multi-valued + requireDimCount all report every document under the path.
 */
public class TestHierarchicalPathValue extends LuceneTestCase {

  // One document labeled with the year only, one with year/month.
  private static final String[][] PATHS = {{"2010"}, {"2010", "10"}};

  private static Number[] taxonomyValues(FacetsConfig config) throws Exception {
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig());
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    for (String[] path : PATHS) {
      Document doc = new Document();
      doc.add(new FacetField("Publish Date", path));
      writer.addDocument(config.build(taxoWriter, doc));
    }
    DirectoryReader reader = DirectoryReader.open(writer);
    TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);
    IndexSearcher searcher = newSearcher(reader);
    DrillDownQuery q = new DrillDownQuery(config);
    q.add("Publish Date", "2010");
    int drillDownHits = searcher.count(q);
    FacetsCollector fc = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
    Facets facets = new FastTaxonomyFacetCounts(taxoReader, config, fc);
    FacetResult top = facets.getTopChildren(10, "Publish Date", "2010");
    FacetResult all = facets.getAllChildren("Publish Date", "2010");
    Number specific = facets.getSpecificValue("Publish Date", "2010");
    IOUtils.close(reader, taxoReader, writer, taxoWriter, dir, taxoDir);
    return new Number[] {top.value, all.value, specific, drillDownHits};
  }

  public void testSingleValuedHierarchicalTaxonomyPathValue() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("Publish Date", true);
    Number[] v = taxonomyValues(config);
    String got =
        "getTopChildren.value="
            + v[0]
            + " getAllChildren.value="
            + v[1]
            + " getSpecificValue="
            + v[2]
            + " drillDownHits="
            + v[3];
    // Both documents contain a value for the path Publish Date/2010:
    assertEquals(got, 2, v[2].intValue());
    assertEquals(got, 2, v[3].intValue());
    assertEquals(got, 2, v[0].intValue());
    assertEquals(got, 2, v[1].intValue());
  }

  /** Control: multi-valued + requireDimCount on the same documents reports 2. */
  public void testMultiValuedRequireDimCountTaxonomyPathValue() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("Publish Date", true);
    config.setMultiValued("Publish Date", true);
    config.setRequireDimCount("Publish Date", true);
    Number[] v = taxonomyValues(config);
    assertEquals(2, v[0].intValue());
    assertEquals(2, v[1].intValue());
  }

  /** Control: SortedSetDocValues faceting with the single-valued hierarchical config reports 2. */
  public void testSingleValuedHierarchicalSortedSetPathValue() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("Publish Date", true);
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig());
    for (String[] path : PATHS) {
      Document doc = new Document();
      doc.add(new SortedSetDocValuesFacetField("Publish Date", path));
      writer.addDocument(config.build(doc));
    }
    DirectoryReader reader = DirectoryReader.open(writer);
    IndexSearcher searcher = newSearcher(reader);
    FacetsCollector fc = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
    Facets facets =
        new SortedSetDocValuesFacetCounts(
            new DefaultSortedSetDocValuesReaderState(reader, config), fc);
    assertEquals(2, facets.getTopChildren(10, "Publish Date", "2010").value.intValue());
    assertEquals(2, facets.getAllChildren("Publish Date", "2010").value.intValue());
    IOUtils.close(reader, writer, dir);
  }
}
