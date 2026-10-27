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
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter.MemoryOrdinalMap;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * OrdinalMappingLeafReader (used by TaxonomyMergeUtils.merge) re-maps the ordinals of the index
 * fields listed in getDimConfigs() plus the static DEFAULT_DIM_CONFIG field. A FacetsConfig whose
 * default dimension config (getDefaultDimConfig, LUCENE-5479) names another index field is not
 * covered, so merged documents keep their source ordinals, which in the destination taxonomy name
 * other labels.
 */
public class TestMergeDefaultDimConfigField extends LuceneTestCase {

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

  private static FacetsConfig configWithDefaultField(String field) {
    return new FacetsConfig() {
      @Override
      protected DimConfig getDefaultDimConfig() {
        DimConfig dimConfig = new DimConfig();
        dimConfig.indexFieldName = field;
        return dimConfig;
      }
    };
  }

  private static void mergeAndCheck(FacetsConfig config, String field) throws Exception {
    // destination: one document, color/red
    Directory destDir = newDirectory();
    Directory destTaxoDir = newDirectory();
    IndexWriter destWriter = new IndexWriter(destDir, newIndexWriterConfig());
    DirectoryTaxonomyWriter destTaxoWriter = new DirectoryTaxonomyWriter(destTaxoDir);
    Document doc = new Document();
    doc.add(new FacetField("color", "red"));
    destWriter.addDocument(config.build(destTaxoWriter, doc));
    destWriter.commit();
    destTaxoWriter.commit();

    // source: one document, size/large (its ordinals collide with color, color/red)
    Directory srcDir = newDirectory();
    Directory srcTaxoDir = newDirectory();
    IndexWriter srcWriter = new IndexWriter(srcDir, newIndexWriterConfig());
    DirectoryTaxonomyWriter srcTaxoWriter = new DirectoryTaxonomyWriter(srcTaxoDir);
    doc = new Document();
    doc.add(new FacetField("size", "large"));
    srcWriter.addDocument(config.build(srcTaxoWriter, doc));
    IOUtils.close(srcWriter, srcTaxoWriter);

    TaxonomyMergeUtils.merge(
        srcDir, srcTaxoDir, new MemoryOrdinalMap(), destWriter, destTaxoWriter, config);

    DirectoryReader reader = DirectoryReader.open(destWriter);
    TaxonomyReader taxoReader = new DirectoryTaxonomyReader(destTaxoWriter);
    IndexSearcher searcher = newSearcher(reader);
    FacetsCollector fc = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
    Facets facets = new FastTaxonomyFacetCounts(field, taxoReader, config, fc);
    // expected: color/red on the destination document, size/large on the merged one
    String color = describe(facets.getTopChildren(10, "color"));
    String size = describe(facets.getTopChildren(10, "size"));
    IOUtils.close(
        reader, taxoReader, destWriter, destTaxoWriter, destDir, destTaxoDir, srcDir, srcTaxoDir);
    assertEquals("color: " + color + ", size: " + size, "value=1 red=1", color);
    assertEquals("color: " + color + ", size: " + size, "value=1 large=1", size);
  }

  /** The default config names its own index field: the merged ordinals are not re-mapped. */
  public void testMergeWithDefaultDimConfigIndexField() throws Exception {
    FacetsConfig config = configWithDefaultField("myfacets");
    assertEquals("myfacets", config.getDimConfig("size").indexFieldName);
    mergeAndCheck(config, "myfacets");
  }

  /** Control: the same field configured explicitly for both dimensions is re-mapped correctly. */
  public void testMergeWithExplicitIndexField() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setIndexFieldName("color", "myfacets");
    config.setIndexFieldName("size", "myfacets");
    mergeAndCheck(config, "myfacets");
  }
}
