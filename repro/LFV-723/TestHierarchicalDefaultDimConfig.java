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
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

public class TestHierarchicalDefaultDimConfig extends LuceneTestCase {

  public void testUnconfiguredHierarchicalDimIsRolledUp() throws Exception {
    // Every dimension without an explicit entry is hierarchical (as in
    // TestFacetsConfig#testCustomDefault); "Publish Date" has no explicit entry.
    FacetsConfig config =
        new FacetsConfig() {
          @Override
          protected DimConfig getDefaultDimConfig() {
            DimConfig dimConfig = new DimConfig();
            dimConfig.hierarchical = true;
            return dimConfig;
          }
        };

    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig());
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    String[][] dates = {{"2010", "10", "15"}, {"2010", "10", "20"}, {"2012", "1", "1"}};
    for (String[] date : dates) {
      Document doc = new Document();
      doc.add(new FacetField("Publish Date", date));
      writer.addDocument(config.build(taxoWriter, doc));
    }
    DirectoryReader reader = DirectoryReader.open(writer);
    TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);

    Facets facets =
        new FastTaxonomyFacetCounts(
            FacetsConfig.DEFAULT_INDEX_FIELD_NAME, reader, taxoReader, config);

    // What config.setHierarchical("Publish Date", true) gives for the same documents:
    assertEquals(
        "dim=Publish Date path=[] value=3 childCount=2\n  2010 (2)\n  2012 (1)\n",
        String.valueOf(facets.getTopChildren(10, "Publish Date")));

    IOUtils.close(reader, taxoReader, writer, taxoWriter, dir, taxoDir);
  }
}
