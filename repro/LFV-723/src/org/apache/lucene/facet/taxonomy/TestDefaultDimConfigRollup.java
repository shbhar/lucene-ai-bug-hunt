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
import org.apache.lucene.facet.LabelAndValue;
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
 * A FacetsConfig whose default dimension config is hierarchical (the customization LUCENE-5479
 * added, see TestFacetsConfig#testCustomDefault) indexes single-valued hierarchical dimensions
 * without their ancestor ordinals, but taxonomy faceting only rolls up the dimensions in
 * getDimConfigs(), i.e. the ones configured explicitly. The counts of every non-leaf label of such
 * a dimension are therefore wrong.
 */
public class TestDefaultDimConfigRollup extends LuceneTestCase {

  private static final String[][] DATES = {
    {"2010", "10", "15"}, {"2010", "10", "20"}, {"2012", "1", "1"}
  };

  /** Hierarchical by default, as in TestFacetsConfig#testCustomDefault. */
  private static FacetsConfig hierarchicalByDefault() {
    return new FacetsConfig() {
      @Override
      protected DimConfig getDefaultDimConfig() {
        DimConfig config = new DimConfig();
        config.hierarchical = true;
        return config;
      }
    };
  }

  private static String describe(FacetResult r) {
    if (r == null) {
      return "null";
    }
    StringBuilder sb = new StringBuilder("value=" + r.value);
    for (LabelAndValue lv : r.labelValues) {
      sb.append(' ').append(lv.label).append('=').append(lv.value);
    }
    return sb.toString();
  }

  public void testHierarchicalDefaultDimConfigCounts() throws Exception {
    FacetsConfig config = hierarchicalByDefault();
    assertTrue(config.getDimConfig("Publish Date").hierarchical);
    assertFalse(config.isDimConfigured("Publish Date"));

    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig());
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    for (String[] date : DATES) {
      Document doc = new Document();
      doc.add(new FacetField("Publish Date", date));
      writer.addDocument(config.build(taxoWriter, doc));
    }
    DirectoryReader reader = DirectoryReader.open(writer);
    TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);
    IndexSearcher searcher = newSearcher(reader);

    // Drill-down sees all three documents under 2010 / 2012:
    DrillDownQuery q2010 = new DrillDownQuery(config);
    q2010.add("Publish Date", "2010");
    assertEquals(2, searcher.count(q2010));

    FacetsCollector fc = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
    Facets counts = new FastTaxonomyFacetCounts(taxoReader, config, fc);
    Facets countAll =
        new FastTaxonomyFacetCounts(
            FacetsConfig.DEFAULT_INDEX_FIELD_NAME, reader, taxoReader, config);

    for (Facets facets : new Facets[] {counts, countAll}) {
      String which = facets == counts ? "hits" : "count-all";
      FacetResult top = facets.getTopChildren(10, "Publish Date");
      // Expected (and what an explicit setHierarchical("Publish Date", true) gives):
      // value=3 2010=2 2012=1
      assertEquals(which + ": " + describe(top), "value=3 2010=2 2012=1", describe(top));
      FacetResult months = facets.getTopChildren(10, "Publish Date", "2010");
      assertEquals(which + ": " + describe(months), "value=2 10=2", describe(months));
      assertEquals(which, 2, facets.getSpecificValue("Publish Date", "2010"));
      assertEquals(which, 3, facets.getSpecificValue("Publish Date"));
    }

    IOUtils.close(reader, taxoReader, writer, taxoWriter, dir, taxoDir);
  }

  /** Control: the same index with the dimension configured explicitly is counted correctly. */
  public void testExplicitHierarchicalConfigCounts() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("Publish Date", true);

    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig());
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    for (String[] date : DATES) {
      Document doc = new Document();
      doc.add(new FacetField("Publish Date", date));
      writer.addDocument(config.build(taxoWriter, doc));
    }
    DirectoryReader reader = DirectoryReader.open(writer);
    TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);
    IndexSearcher searcher = newSearcher(reader);
    FacetsCollector fc = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
    Facets facets = new FastTaxonomyFacetCounts(taxoReader, config, fc);
    assertEquals("value=3 2010=2 2012=1", describe(facets.getTopChildren(10, "Publish Date")));
    assertEquals("value=2 10=2", describe(facets.getTopChildren(10, "Publish Date", "2010")));
    assertEquals(2, facets.getSpecificValue("Publish Date", "2010"));
    assertEquals(3, facets.getSpecificValue("Publish Date"));
    IOUtils.close(reader, taxoReader, writer, taxoWriter, dir, taxoDir);
  }
}
