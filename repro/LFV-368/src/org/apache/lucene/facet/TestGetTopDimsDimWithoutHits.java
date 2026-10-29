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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestGetTopDimsDimWithoutHits extends LuceneTestCase {

  private static Document doc(String id, IndexableField facet) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.NO));
    doc.add(facet);
    return doc;
  }

  public void testMultiValuedDimWithoutHits() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setMultiValued("a", true); // requireDimCount stays false (the default)
    config.setMultiValued("b", true);
    try (Directory taxoIndexDir = new ByteBuffersDirectory();
        Directory taxoDir = new ByteBuffersDirectory();
        Directory ssdvIndexDir = new ByteBuffersDirectory();
        IndexWriter taxoIndex = new IndexWriter(taxoIndexDir, new IndexWriterConfig());
        DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
        IndexWriter ssdvIndex = new IndexWriter(ssdvIndexDir, new IndexWriterConfig())) {
      for (String dim : new String[] {"a", "b"}) {
        taxoIndex.addDocument(config.build(taxoWriter, doc(dim, new FacetField(dim, "x"))));
        ssdvIndex.addDocument(config.build(doc(dim, new SortedSetDocValuesFacetField(dim, "x"))));
      }
      TermQuery onlyB = new TermQuery(new Term("id", "b"));
      try (DirectoryReader taxoReader = DirectoryReader.open(taxoIndex);
          DirectoryTaxonomyReader taxonomy = new DirectoryTaxonomyReader(taxoWriter);
          DirectoryReader ssdvReader = DirectoryReader.open(ssdvIndex)) {
        Facets taxo =
            new FastTaxonomyFacetCounts(
                taxonomy,
                config,
                new IndexSearcher(taxoReader).search(onlyB, new FacetsCollectorManager()));
        Facets ssdv =
            new SortedSetDocValuesFacetCounts(
                new DefaultSortedSetDocValuesReaderState(ssdvReader, config),
                new IndexSearcher(ssdvReader).search(onlyB, new FacetsCollectorManager()));
        // expected: getTopDims is the getAllDims prefix [b]; actual: [null, b] and [null] (-ea: AssertionError)
        assertEquals(
            "taxonomy " + taxo.getAllDims(10) + " ssdv " + ssdv.getAllDims(10),
            "taxonomy " + taxo.getTopDims(10, 10) + " ssdv " + ssdv.getTopDims(1, 10));
      }
    }
  }
}
