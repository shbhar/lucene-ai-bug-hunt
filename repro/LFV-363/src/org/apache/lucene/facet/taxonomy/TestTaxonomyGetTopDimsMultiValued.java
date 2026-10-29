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

public class TestTaxonomyGetTopDimsMultiValued extends LuceneTestCase {

  public void testOlderDimWithMoreHits() throws Exception {
    FacetsConfig config = new FacetsConfig();
    for (String dim : new String[] {"x", "y"}) {
      config.setMultiValued(dim, true);
      config.setRequireDimCount(dim, true);
    }
    try (Directory dir = new ByteBuffersDirectory();
        Directory taxoDir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig());
        DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir)) {
      Document doc = new Document();
      doc.add(new FacetField("x", "x1"));
      writer.addDocument(config.build(taxoWriter, doc));
      doc = new Document();
      doc.add(new FacetField("x", "x2"));
      doc.add(new FacetField("y", "y1"));
      writer.addDocument(config.build(taxoWriter, doc));
      try (DirectoryReader reader = DirectoryReader.open(writer);
          DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter)) {
        FacetsCollector hits =
            new IndexSearcher(reader)
                .search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
        Facets facets = new FastTaxonomyFacetCounts(taxoReader, config, hits);
        // Facets#getTopDims javadoc: the same as getAllDims, then using only the first topNDims
        assertEquals(facets.getAllDims(10).subList(0, 1), facets.getTopDims(1, 10));
      }
    }
  }
}
